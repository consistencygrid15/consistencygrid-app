package com.consistencygridwallpaper.reelcontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.consistencygridwallpaper.reelcontrol.data.PreferencesManager
import com.consistencygridwallpaper.reelcontrol.detection.ScrollDetectionEngine
import com.consistencygridwallpaper.reelcontrol.manager.BlockManager
import com.consistencygridwallpaper.reelcontrol.manager.OverlayManager
import com.consistencygridwallpaper.reelcontrol.manager.PopupManager
import com.consistencygridwallpaper.reelcontrol.manager.ReelManager
import com.consistencygridwallpaper.reelcontrol.workers.ReelWatchdogWorker
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ReelTrackingService — Production-grade AccessibilityService.
 *
 * Architecture:
 *  - Multi-layer reel detection (view ID + class name + geometry via ScrollDetectionEngine)
 *  - Strict vertical-scroll-only counting (anti-false-positive)
 *  - Block mode: soft overlay or hard home action
 *  - Heartbeat: 1-second cleanup loop via OverlayManager
 *  - KeepAliveService: foreground notification prevents OS kill
 *  - Full try/catch on every block — NEVER crashes
 */
class ReelTrackingService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelTracker"
        private const val HEARTBEAT_MS    = 1500L
        // Dedicated HUD refresh rate — independent of the session-expiry heartbeat.
        // 500ms feels instant/live without causing noticeable battery impact.
        private const val HUD_REFRESH_MS  = 500L
        private const val TREE_SCAN_DEBOUNCE_MS = 80L

        @Volatile
        var isRunning = false

        val TRACKED_PACKAGES = setOf(
            "com.instagram.android",
            "com.google.android.youtube"
            // "com.snapchat.android",       // DISABLED — removed from tracking
            // "com.zhiliaoapp.musically"    // DISABLED — removed from tracking
        )

        // App display names for block overlay messages
        private val APP_NAMES = mapOf(
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube"
            // "com.snapchat.android" to "Snapchat",    // DISABLED
            // "com.zhiliaoapp.musically" to "TikTok"   // DISABLED
        )

        private const val NON_REEL_HYSTERESIS_MAX = 4
        // ISSUE 15 FIX: Lowered from 200ms → 150ms so 4 non-reel scans = 600ms exit latency
        // (was 800ms). On TYPE_WINDOW_STATE_CHANGED the service exits immediately anyway —
        // this only affects intra-app fragment navigation (e.g. Instagram Reels → DMs).
        private const val CONTENT_SCAN_INTERVAL_IN_REEL_MS = 150L
        private const val CONTENT_SCAN_INTERVAL_OUT_REEL_MS = 150L
    }

    // ─── Managers ─────────────────────────────────────────────────────────────
    private lateinit var reelManager: ReelManager
    private lateinit var overlayManager: OverlayManager
    private lateinit var popupManager: PopupManager
    private lateinit var blockManager: BlockManager
    private lateinit var prefs: PreferencesManager
    private val scrollEngine = ScrollDetectionEngine()

    // ─── Coroutine scope ──────────────────────────────────────────────────────
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    // ─── State ────────────────────────────────────────────────────────────────
    // FIX: @Volatile ensures cross-thread visibility. currentPackage and isInReelMode are
    // written on the accessibility main thread but READ from coroutine collectors
    // (observeLimitState / observeBlockMode). Without @Volatile, changes may not
    // be immediately visible to the reading thread, causing stale reads.
    @Volatile private var currentPackage = ""
    @Volatile private var isInReelMode = false
    private var lastTreeScanMs = 0L

    // Heartbeat Runnable — checks session expiry (1500ms)
    private var heartbeatRunnable: Runnable? = null
    // Dedicated HUD refresh loop — pushes live count+time to overlay (500ms)
    private var hudRefreshRunnable: Runnable? = null

    // Screen off receiver
    private var screenOffReceiver: BroadcastReceiver? = null

    // Account logout listener — detects Instagram/YouTube/TikTok/Snapchat account removal
    private var packageDataReceiver: BroadcastReceiver? = null

    // BUG 3 FIX: configReceiver promoted to class field so cleanup() can unregister it.
    // Previously a local variable → leaked on every onServiceConnected() reconnect,
    // stacking duplicate receivers and causing duplicate reel counts + UI flicker.
    private var configReceiver: BroadcastReceiver? = null

    // Follow-up scan runnable for instant detection after app switch
    private var appSwitchScanRunnable: Runnable? = null

    // ─────────────────────────────────────────────────────────────────────────
    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            isRunning = true

            // Init all managers
            // ISSUE 14 FIX: Invalidate the singleton before getting it so that if the OS
            // restarted the service (e.g. after an OEM kill), we get a fresh ReelManager
            // instead of one with stale in-memory counts from the previous process lifetime.
            // setActivePackage() will reload from prefs anyway, but invalidating ensures the
            // StateFlows also start at clean zero rather than possibly stale cached values.
            ReelManager.invalidate()
            reelManager    = ReelManager.getInstance(applicationContext)
            overlayManager = OverlayManager(this)
            popupManager   = PopupManager(this)
            blockManager   = BlockManager(this)
            prefs          = PreferencesManager(applicationContext)
            // FIX: Provide context to ScrollDetectionEngine so it can use WindowMetrics
            // (API 30+) for accurate display dimensions in split-screen / foldable modes.
            scrollEngine.init(applicationContext)

            // Reconfigure serviceInfo at runtime for reliability & instant response
            serviceInfo = serviceInfo?.also { info ->
                info.eventTypes = (
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
                )
                info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                info.flags = (
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                )
                info.notificationTimeout = 0L
            }

            // Start observers
            observeLimitState()
            observeBlockMode()
            startHeartbeat()

            // Register screen off receiver for instant hide
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            screenOffReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, int: Intent?) {
                    Log.d(TAG, "Screen off detected -> instant hide")
                    if (isInReelMode) exitReelMode()
                }
            }
            registerReceiver(screenOffReceiver, filter)

            // Register logout detector — fires when user clears app data or uninstalls
            // (which happens during logout on Instagram, TikTok, Snapchat on most OEMs)
            val logoutFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_DATA_CLEARED)
                addDataScheme("package")
            }
            packageDataReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val pkg = intent?.data?.schemeSpecificPart ?: return
                    if (pkg in TRACKED_PACKAGES) {
                        Log.d(TAG, "📦 Package data cleared for $pkg -> resetting reel stats")
                        try {
                            prefs.resetStatsForPackage(pkg)
                            reelManager.resetCountForPackage(pkg)
                            if (isInReelMode && currentPackage == pkg) exitReelMode()

                            // Open our app's dashboard so user sees the reset stats
                            val dashIntent = Intent(applicationContext, com.consistencygridwallpaper.MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra("from_logout", pkg)
                                putExtra("bypass_onboarding", true)
                                putExtra("open_screen", "dashboard")
                            }
                            applicationContext.startActivity(dashIntent)
                            Log.d(TAG, "🏠 Opened dashboard after logout of $pkg")
                        } catch (e: Exception) {
                            Log.e(TAG, "Logout reset error: ${e.message}")
                        }
                    }
                }
            }
            registerReceiver(packageDataReceiver, logoutFilter)

            // Ensure KeepAliveService is running
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, KeepAliveService::class.java))
                } else {
                    startService(Intent(this, KeepAliveService::class.java))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start KeepAliveService: ${e.message}")
            }

            // Register config receiver — updates limits in real-time when user edits settings in app
            val configFilter = IntentFilter("com.consistencygridwallpaper.ACTION_CONFIG_CHANGED")
            configReceiver?.let {
                try { unregisterReceiver(it) } catch (_: Exception) {}
            }
            configReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, int: Intent?) {
                    Log.d(TAG, "⚙ Config change broadcast received → refreshing ReelManager limits")

                    // ─── FIX: Multi-process stale SharedPreferences fix ─────────────────
                    // The service runs in android:process=":reelservice" — a separate OS process.
                    // Each process has its own in-memory EncryptedSharedPreferences cache that
                    // does NOT auto-sync when the UI process writes new values to the same file.
                    //
                    // Previously: configReceiver called prefs.getReelLimit() which read from the
                    // SERVICE's stale in-memory cache → got the OLD limit → limitReached stayed true.
                    //
                    // Fix: The UI now embeds the NEW values directly in the broadcast Intent extras.
                    // We apply them to the service's prefs FIRST (updating the in-memory cache),
                    // then call refreshLimitsForPackage which now reads the CORRECT fresh value.
                    //
                    // Also fixed: We now refresh for `changedPkg` REGARDLESS of currentPackage.
                    // Previously the condition `currentPackage in TRACKED_PACKAGES` was false when
                    // user was in our settings app (not Instagram) → refresh was skipped entirely.
                    val changedPkg   = int?.getStringExtra("changed_pkg") ?: ""
                    val newReelLimit = int?.getIntExtra("new_reel_limit", -1) ?: -1
                    val newTimeLimit = int?.getIntExtra("new_time_limit", -1) ?: -1

                    if (changedPkg.isNotEmpty() && newReelLimit >= 0 && newTimeLimit >= 0) {
                        // Apply new limit values directly to SERVICE's prefs in-memory cache
                        // so that subsequent prefs.getReelLimit()/getTimeLimit() calls return
                        // the correct value without waiting for a file read.
                        prefs.setReelLimitDirect(changedPkg, newReelLimit)
                        prefs.setTimeLimitDirect(changedPkg, newTimeLimit)
                        // Force refresh for the changed package — works even when user is
                        // currently NOT on that package (e.g., user is in our settings app).
                        reelManager.clearSnooze()
                        reelManager.refreshLimitsForPackage(changedPkg)
                        Log.d(TAG, "✅ Limit update applied for $changedPkg: reels=$newReelLimit, time=${newTimeLimit}m")
                    } else if (currentPackage.isNotEmpty() && currentPackage in TRACKED_PACKAGES) {
                        // Generic config change (block mode toggle, app enable/disable, etc.)
                        reelManager.refreshLimitsForPackage(currentPackage)
                    }

                    if (!reelManager.limitReached.value) {
                        popupManager.dismiss()
                    }
                    if (currentPackage.isNotEmpty() && isInReelMode) {
                        if (!reelManager.limitReached.value && !prefs.isBlockModeEnabled) {
                            overlayManager.show(
                                reelManager.reelCount.value,
                                reelManager.watchTime.value,
                                reelManager.getLimit(),
                                reelManager.getTimeLimit()
                            )
                        } else if (reelManager.limitReached.value || prefs.isBlockModeEnabled) {
                            overlayManager.hide()
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                this,
                configReceiver,
                configFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            // Schedule the watchdog to detect if this service gets killed by OEM
            try {
                ReelWatchdogWorker.schedule(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog scheduling skipped in service process: ${e.message}")
            }

            Log.d(TAG, "✅ Service connected")
        } catch (e: Exception) {
            Log.e(TAG, "❌ onServiceConnected crashed: ${e.message}", e)
        }
    }

    private fun getRootNodeFromEvent(ev: AccessibilityEvent): AccessibilityNodeInfo? {
        try {
            val root = rootInActiveWindow
            if (root != null) return root
        } catch (_: Exception) {}

        // BUG 2 FIX: ev.source is system-managed — we must NOT recycle it directly.
        // Take our own copy via obtain() so we own the reference and can safely
        // traverse + recycle intermediate nodes without corrupting the event's source.
        val sourceCopy = try {
            val src = ev.source ?: return null
            AccessibilityNodeInfo.obtain(src)
        } catch (_: Exception) { return null }

        var current: AccessibilityNodeInfo? = sourceCopy
        while (current != null) {
            val parent = try { current.parent } catch (_: Exception) { null }
            if (parent == null) {
                // current IS the root — caller will recycle it via safeRecycle()
                return current
            }
            // Recycle the intermediate copy; move up to parent
            safeRecycle(current)
            current = parent
        }
        return null
    }

    // ─── Brainpal-Style State & Hysteresis Engine ──────────────────────────
    private var consecutiveNonReelScans = 0

    // ─────────────────────────────────────────────────────────────────────────
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        var rootNode: AccessibilityNodeInfo? = null
        try {
            val ev = event ?: return
            if (!::prefs.isInitialized) return
            if (!prefs.isFeatureEnabled) return

            val eventType = ev.eventType
            val isWindowStateChange = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                                     eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

            // ── Package resolution ────────────────────────────────────────────
            val activePkg: String = if (isWindowStateChange) {
                ev.packageName?.toString() ?: currentPackage
            } else {
                try {
                    rootNode = getRootNodeFromEvent(ev)
                    rootNode?.packageName?.toString()
                        ?: ev.packageName?.toString()
                        ?: currentPackage
                } catch (_: Exception) {
                    ev.packageName?.toString() ?: currentPackage
                }
            }

            if (activePkg.isEmpty()) return

            // ── App / window switch handling ──────────────────────────────────
            if (isWindowStateChange || activePkg != currentPackage) {
                if (isWindowStateChange) {
                    Log.d(TAG, "⚡ Window state change received for pkg=$activePkg (current=$currentPackage, inReelMode=$isInReelMode)")
                }
                if (activePkg != currentPackage) {
                    handleAppSwitch(activePkg)
                } else {
                    // Same package, new window (e.g. Reels → DMs within Instagram)
                    // Reset hysteresis counter and force immediate re-scan.
                    // NOTE: We cannot call exitReelMode() here unconditionally because
                    // TYPE_WINDOW_STATE_CHANGED fires constantly within Instagram Reels
                    // (on every new video load) — doing so would break the HUD counter.
                    consecutiveNonReelScans = 0
                    if (isInReelMode) {
                        Log.d(TAG, "Window changed within $activePkg -> re-scanning screen")
                    }
                    scrollEngine.resetSurfaceCache()
                    lastTreeScanMs = 0L // force immediate re-scan
                }
            }

            // ── Not a tracked package — exit early ────────────────────────────
            if (activePkg !in TRACKED_PACKAGES) {
                // Instantly dismiss any popup/overlay if user left tracked app (e.g. went Home)
                if (popupManager.isShowing() || blockManager.isShowing() || overlayManager.isShowing()) {
                    Log.d(TAG, "User switched to non-tracked package '$activePkg' -> dismissing all overlays/popups")
                    popupManager.dismiss()
                    blockManager.hide()
                    overlayManager.hideImmediate()
                }
                if (isInReelMode) exitReelMode()
                return
            }
            if (!prefs.isAppEnabled(activePkg)) {
                if (isInReelMode) exitReelMode()
                return
            }

            // ── Scheduled Pause check ─────────────────────────────────────────
            if (prefs.isPaused()) {
                // BUG-P FIX: Scheduled Pause is an INTENTIONAL user action to grant temporary
                // unrestricted access. We must dismiss popup/block regardless of limitReached state.
                // FIX-C prevents exitReelMode from dismissing popup when limitReached=true, which
                // is correct for hysteresis scenarios — but NOT for scheduled pauses where the user
                // explicitly chose to take a break and should be able to use the app freely.
                popupManager.dismiss()
                blockManager.hide()
                if (isInReelMode) exitReelMode()
                return
            }

            // ── Tree scan: detect reel screen ─────────────────────────────────
            val isViewFocused = eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || isWindowStateChange || isViewFocused) {
                val now = System.currentTimeMillis()
                if (isWindowStateChange) scrollEngine.resetSurfaceCache()
                
                val scanInterval = if (isInReelMode) CONTENT_SCAN_INTERVAL_IN_REEL_MS else CONTENT_SCAN_INTERVAL_OUT_REEL_MS
                val shouldScan = isWindowStateChange || (now - lastTreeScanMs > scanInterval)
                if (shouldScan) {
                    lastTreeScanMs = now
                    if (rootNode == null) rootNode = try { rootInActiveWindow } catch (_: Exception) { null }
                    scanWindowForReelMode(activePkg, rootNode, isWindowStateChange)
                    rootNode = null // ownership transferred
                }
            }

            // ── Scroll event: count reel ──────────────────────────────────────
            if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (!isInReelMode) {
                    val now = System.currentTimeMillis()
                    if (now - lastTreeScanMs > CONTENT_SCAN_INTERVAL_OUT_REEL_MS) {
                        lastTreeScanMs = now
                        if (rootNode == null) rootNode = try { rootInActiveWindow } catch (_: Exception) { null }
                        scanWindowForReelMode(activePkg, rootNode, false)
                        rootNode = null
                    }
                }

                if (isInReelMode && scrollEngine.isValidReelScroll(ev, activePkg)) {
                    Log.d(TAG, "Scroll validated for $activePkg -> incrementing ReelManager")
                    reelManager.onScrollDetected(activePkg)
                    if (!reelManager.limitReached.value) {
                        overlayManager.update(
                            reelManager.reelCount.value,
                            reelManager.watchTime.value,
                            reelManager.getLimit(),
                            reelManager.getTimeLimit()
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ onAccessibilityEvent error: ${e.message}", e)
        } finally {
            safeRecycle(rootNode)
        }
    }

    // ─── Window Tree Scan with Hysteresis ─────────────────────────────────────
    private fun scanWindowForReelMode(activePkg: String, providedRoot: AccessibilityNodeInfo?, isWindowStateChange: Boolean) {
        // FIX B: Skip scanning when popup or block overlay is showing.
        // When popup is fullscreen, the user can't scroll reels — there's no point scanning
        // the tree. More importantly, if for any reason the popup still has focus (fallback path),
        // scanning would find the popup window (not Instagram's reels), causing hysteresis to fire
        // and exitReelMode() to dismiss the popup immediately after it appears.
        if (popupManager.isShowing() || blockManager.isShowing()) {
            consecutiveNonReelScans = 0  // Reset hysteresis so we don't exit on resume
            return
        }

        // BUG 6 FIX: Only recycle the rootNode if WE acquired it here.
        // If it came from the caller (providedRoot), the caller owns it and will recycle it.
        val ownedByUs: Boolean
        val rootNode: AccessibilityNodeInfo?
        if (providedRoot != null) {
            rootNode = providedRoot
            ownedByUs = false
        } else {
            rootNode = try { rootInActiveWindow } catch (_: Exception) { null }
            ownedByUs = true
        }

        if (rootNode == null) return

        try {
            val inReels = try {
                scrollEngine.isReelScreenVisible(rootNode, activePkg)
            } catch (_: Exception) { false }

            Log.d(TAG, "scanWindowForReelMode: pkg=$activePkg, inReels=$inReels, currentInReelMode=$isInReelMode, hysteresis=$consecutiveNonReelScans")
            
            if (inReels) {
                consecutiveNonReelScans = 0
                if (!isInReelMode) enterReelMode(activePkg)
            } else {
                if (isInReelMode) {
                    consecutiveNonReelScans++
                    // On explicit Window State Change (Activity/Fragment switch), exit immediately without waiting
                    if (isWindowStateChange || consecutiveNonReelScans >= NON_REEL_HYSTERESIS_MAX) {
                        Log.d(TAG, "Non-reel screen confirmed (stateChange=$isWindowStateChange, count=$consecutiveNonReelScans) -> exiting reel mode")
                        consecutiveNonReelScans = 0
                        exitReelMode()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanWindowForReelMode error: ${e.message}", e)
        } finally {
            // Only recycle if we acquired this node ourselves
            if (ownedByUs) safeRecycle(rootNode)
        }
    }


    // ─── Reel Mode Enter/Exit ─────────────────────────────────────────────────
    private fun enterReelMode(pkg: String) {
        try {
            Log.d(TAG, "📺 ENTER reel mode: $pkg")
            isInReelMode = true
            reelManager.enterReelMode()

            if (reelManager.limitReached.value) {
                // Do not count time spent on the limit UI as reel watching.
                reelManager.pauseSessionTiming()
                val isHardBlock = prefs.isHardBlockEnabled
                if (isHardBlock) {
                    blockManager.triggerBlock(
                        isHardBlock = true,
                        appName = APP_NAMES[pkg] ?: pkg,
                        streak = reelManager.getStreak(),
                        onHome = {
                            try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Exception) {}
                        }
                    )
                    isInReelMode = false
                    reelManager.exitReelMode()
                    return
                } else {
                    overlayManager.hide()
                    popupManager.showLimitPopup(
                        reelManager = reelManager,
                        onGoHome = {
                            try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Exception) {}
                        }
                    )
                }
            } else {
                // Limit is NOT reached -> ALWAYS show Floating Counter HUD overlay!
                popupManager.dismiss()
                blockManager.hide()
                overlayManager.show(
                    reelManager.reelCount.value,
                    reelManager.watchTime.value,
                    reelManager.getLimit(),
                    reelManager.getTimeLimit()
                )
                startHudRefresh()
            }
        } catch (e: Exception) {
            Log.e(TAG, "enterReelMode error: ${e.message}", e)
        }
    }

    private fun exitReelMode() {
        try {
            Log.d(TAG, "❌ EXIT reel mode -> ending detection session and hiding overlay immediately")
            isInReelMode = false
            stopHudRefresh()
            scrollEngine.endSession()
            reelManager.exitReelMode()
            overlayManager.hideImmediate()
            // C2 FIX: Only hide the block overlay when limit is NOT reached.
            // Previously exitReelMode() always called blockManager.hide(), which would dismiss
            // the block screen even when the user's limit was genuinely reached and the block
            // was legitimately showing (e.g., user navigated to Instagram DMs for one second
            // then came back — hysteresis exit fired, block dismissed unexpectedly).
            // Now: only dismiss block if the limit state is cleared; let it persist otherwise.
            if (!reelManager.limitReached.value) {
                blockManager.hide()
            }
            // FIX C: Only dismiss the popup if the limit is NOT currently reached.
            // If the user's limit IS reached, the popup should persist even when exitReelMode
            // is triggered (e.g., brief fragment navigation within Instagram). Previously this
            // dismissed the popup unconditionally, causing the endless show-dismiss loop.
            if (!reelManager.limitReached.value) {
                popupManager.dismiss()
            }
        } catch (e: Exception) {
            Log.e(TAG, "exitReelMode error: ${e.message}", e)
        }
    }

    // ─── App Switch ───────────────────────────────────────────────────────────
    private fun handleAppSwitch(newPkg: String) {
        try {
            if (newPkg == currentPackage) return
            Log.d(TAG, "🔄 App Switch: $currentPackage → $newPkg")

            // A switch between tracked apps is a session boundary too. Close the
            // old package session before loading the new package's counters.
            if (isInReelMode) {
                overlayManager.hideImmediate()
                blockManager.hide()
                popupManager.dismiss()
                exitReelMode()
            }

            currentPackage = newPkg
            scrollEngine.endSession()

            if (newPkg in TRACKED_PACKAGES && prefs.isAppEnabled(newPkg)) {
                // M1 FIX: Clear any stale snooze from the PREVIOUS session before entering.
                // Without this, a 5-min snooze granted in session A would silently suppress the
                // block popup in session B (even after the user left and came back).
                // Example: hit limit in Instagram, snooze, switch to WhatsApp, come back —
                // the snooze was still active and no block would show despite limit being reached.
                reelManager.clearSnooze()
                reelManager.setActivePackage(newPkg)
                // Schedule instant follow-up scans at +150ms, +400ms, +650ms to catch view tree rendering
                scheduleAppSwitchFollowUpScans(newPkg)
            } else {
                appSwitchScanRunnable?.let { handler.removeCallbacks(it) }
                appSwitchScanRunnable = null
                // Immediately hide overlay & popup so user sees instant response.
                overlayManager.hideImmediate()
                blockManager.hide()
                popupManager.dismiss()
                if (isInReelMode) exitReelMode()
                reelManager.setActivePackage("")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleAppSwitch error: ${e.message}", e)
        }
    }

    private fun scheduleAppSwitchFollowUpScans(pkg: String) {
        appSwitchScanRunnable?.let { handler.removeCallbacks(it) }
        var attempts = 0
        val runnable = object : Runnable {
            override fun run() {
                attempts++
                if (currentPackage != pkg || pkg !in TRACKED_PACKAGES) return
                Log.d(TAG, "⏰ App switch follow-up scan #$attempts for $pkg (isInReelMode=$isInReelMode)")
                scanWindowForReelMode(pkg, null, isWindowStateChange = false)
                // R4 FIX: Extended from 4 to 6 attempts (1.5s total coverage at 250ms each)
                // Slower phones (Snapchat especially) need more time for UI to fully render
                // before the accessibility tree contains the Spotlight view IDs we scan for.
                if (!isInReelMode && attempts < 6) {
                    handler.postDelayed(this, 250L)
                }
            }
        }
        appSwitchScanRunnable = runnable
        handler.postDelayed(runnable, 150L)
    }

    private fun observeLimitState() {
        scope.launch {
            reelManager.limitReached.collect { reached ->
                try {
                    if (reached) {
                        // The popup/block UI is not watch time. Commit elapsed time
                        // once and restart the clock only after a snooze.
                        reelManager.pauseSessionTiming()
                        // M5 FIX: Check isHardBlockEnabled in observeLimitState.
                        // Previously this path only showed the soft popup regardless of block mode.
                        // If user has Hard Block enabled, we should fire the home action immediately
                        // instead of showing the soft mindful-pause popup.
                        if (prefs.isHardBlockEnabled) {
                            Log.w(TAG, "🚨 Limit reached + Hard Block ON — firing home action")
                            popupManager.dismiss()
                            blockManager.hide()
                            overlayManager.hide()
                            try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Exception) {}
                        } else if (!popupManager.isShowing() && !blockManager.isShowing()) {
                            Log.w(TAG, "🚨 Limit reached — showing popup")
                            overlayManager.hide()
                            popupManager.showLimitPopup(
                                reelManager = reelManager,
                                onGoHome = {
                                    try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Exception) {}
                                }
                            )
                        }
                    } else {
                        if (popupManager.isShowing()) {
                            Log.d(TAG, "Limit state cleared -> dismissing popup and restoring HUD overlay")
                            popupManager.dismiss()
                        }
                        if (isInReelMode && currentPackage.isNotEmpty() && !prefs.isBlockModeEnabled) {
                            overlayManager.show(
                                reelManager.reelCount.value,
                                reelManager.watchTime.value,
                                reelManager.getLimit(),
                                reelManager.getTimeLimit()
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "observeLimitState collect error (non-fatal): ${e.message}", e)
                }
            }
        }
    }

    private fun observeBlockMode() {
        scope.launch {
            reelManager.blockMode.collectLatest { blockEnabled ->
                try {
                    // FIX: per-emission try-catch (same pattern as observeLimitState).
                    // Previously the try-catch was OUTSIDE the collectLatest lambda.
                    // Any exception inside the lambda would propagate out, end the coroutine,
                    // and PERMANENTLY stop all future block-mode updates until service restarts.
                    // Now: exception is caught per-emission; collectLatest continues regardless.
                    Log.d(TAG, "Block mode changed: $blockEnabled")
                    if (!blockEnabled && isInReelMode && !overlayManager.isShowing() && !reelManager.limitReached.value) {
                        overlayManager.show(
                            reelManager.reelCount.value,
                            reelManager.watchTime.value,
                            reelManager.getLimit(),
                            reelManager.getTimeLimit()
                        )
                    } else if (blockEnabled && isInReelMode) {
                        overlayManager.hide()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "observeBlockMode collect error (non-fatal): ${e.message}", e)
                    // Do NOT re-throw: the collectLatest loop must survive individual emission failures
                }
            }
        }
    }

    // ─── Heartbeat ────────────────────────────────────────────────────────────
    // Every 1.5s: check session expiry only. Overlay re-show removed to avoid
    // conflict with block mode and limit popup state.
    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                try {
                    // BUG-H FIX: Don't exit reel mode when popup or block overlay is showing.
                    val overlayActive = popupManager.isShowing() || blockManager.isShowing()
                    if (isInReelMode && scrollEngine.isSessionExpired() && !overlayActive) {
                        Log.d(TAG, "💤 Session expired (inactivity) — exiting reel mode")
                        exitReelMode()
                    }
                    // NOTE: HUD updates are handled by the dedicated hudRefreshRunnable (500ms).
                    // No overlay.update() here — avoids double-updating on the same tick.
                } catch (e: Exception) {
                    Log.e(TAG, "heartbeat error: ${e.message}", e)
                }
                handler.postDelayed(this, HEARTBEAT_MS)
            }
        }
        handler.postDelayed(heartbeatRunnable!!, HEARTBEAT_MS)
        Log.d(TAG, "💓 Heartbeat started")
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { handler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    // ─── Dedicated HUD Refresh Loop ───────────────────────────────────────────
    /**
     * Runs every 500ms while in reel mode to keep the HUD counter live.
     *
     * Separate from the heartbeat so:
     *  - Session-expiry logic (1500ms) is unaffected
     *  - HUD refresh rate (500ms) can be tuned independently
     *  - No double-update on the same tick
     */
    private fun startHudRefresh() {
        stopHudRefresh()
        hudRefreshRunnable = object : Runnable {
            override fun run() {
                try {
                    if (isInReelMode && overlayManager.isShowing() && !reelManager.limitReached.value) {
                        overlayManager.update(
                            reelManager.reelCount.value,
                            reelManager.watchTime.value,
                            reelManager.getLimit(),
                            reelManager.getTimeLimit()
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "hudRefresh error: ${e.message}", e)
                }
                if (isInReelMode) handler.postDelayed(this, HUD_REFRESH_MS)
            }
        }
        handler.postDelayed(hudRefreshRunnable!!, HUD_REFRESH_MS)
        Log.d(TAG, "⚡ HUD refresh loop started (${HUD_REFRESH_MS}ms)")
    }

    private fun stopHudRefresh() {
        hudRefreshRunnable?.let { handler.removeCallbacks(it) }
        hudRefreshRunnable = null
    }


    // ─── Lifecycle ────────────────────────────────────────────────────────────
    override fun onInterrupt() {
        Log.w(TAG, "⚠ onInterrupt — cleaning up")
        cleanup()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }


    private fun cleanup() {
        try {
            isRunning = false
            stopHeartbeat()
            stopHudRefresh()
            scope.cancel()
            if (isInReelMode) {
                isInReelMode = false
                reelManager.exitReelMode()
            }
            // C3 FIX: Call destroy() (not hide()) so the view is fully removed from WindowManager.
            // hide() sets visibility=GONE but keeps the window token alive. On OEM phones where
            // the AccessibilityService is force-stopped and restarted, the stale token persists
            // causing a stuck invisible overlay. destroy() calls removeViewImmediate() to clean up.
            overlayManager.destroy()
            blockManager.hide()
            popupManager.dismiss()
            scrollEngine.endSession()
            screenOffReceiver?.let {
                try { unregisterReceiver(it) } catch (_: Exception) {}
                screenOffReceiver = null
            }
            packageDataReceiver?.let {
                try { unregisterReceiver(it) } catch (_: Exception) {}
                packageDataReceiver = null
            }
            // BUG 3 FIX: unregister configReceiver (was missing — caused receiver leak on reconnect)
            configReceiver?.let {
                try { unregisterReceiver(it) } catch (_: Exception) {}
                configReceiver = null
            }
            Log.d(TAG, "🧹 Cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "cleanup error: ${e.message}", e)
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────
    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        try { node?.recycle() } catch (_: Exception) {}
    }
}
