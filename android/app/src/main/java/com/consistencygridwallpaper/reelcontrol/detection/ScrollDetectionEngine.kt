package com.consistencygridwallpaper.reelcontrol.detection

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * ScrollDetectionEngine — Production-grade Reel & Ad Detection Engine.
 *
 * Call [init] once after construction to provide an application context so that
 * display-dimension queries use WindowMetrics (API 30+) rather than the static
 * Resources.getSystem() path which can report stale values in split-screen /
 * foldable multi-window modes.
 */
class ScrollDetectionEngine {

    // FIX: Context is needed for reliable display metrics on API 30+ (WindowMetrics).
    // Set via init() immediately after construction inside ReelTrackingService.
    private var appContext: Context? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
    }

    /** Returns the current display height in pixels using the most reliable API available. */
    private fun getDisplayHeight(): Int {
        val ctx = appContext
        return if (ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.currentWindowMetrics.bounds.height()
            } catch (_: Exception) {
                android.content.res.Resources.getSystem().displayMetrics.heightPixels
            }
        } else {
            android.content.res.Resources.getSystem().displayMetrics.heightPixels
        }
    }

    /** Returns the current display width in pixels using the most reliable API available. */
    private fun getDisplayWidth(): Int {
        val ctx = appContext
        return if (ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.currentWindowMetrics.bounds.width()
            } catch (_: Exception) {
                android.content.res.Resources.getSystem().displayMetrics.widthPixels
            }
        } else {
            android.content.res.Resources.getSystem().displayMetrics.widthPixels
        }
    }

    companion object {
        private const val TAG = "ScrollEngine"
        private const val SCROLL_DEBOUNCE_MS = 450L  // 450ms hard lockout between reel increments
        private const val SESSION_TIMEOUT_MS = 5_000L
        private const val MIN_VIDEO_HEIGHT_PX = 400
        private const val MIN_VIDEO_WIDTH_PX  = 200

        private const val SMALL_AREA_SCREEN_PERCENT = 20

        private val INSTAGRAM_SCREEN_IDS = setOf(
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/reel_viewer_container",
            "com.instagram.android:id/reel_viewer",
            "com.instagram.android:id/clips_viewer",
            "com.instagram.android:id/reel_overlay_container",
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/clips_swipe_refresh_layout",
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_root_container",
            "com.instagram.android:id/reels_viewer_container",
            "com.instagram.android:id/reels_view_pager"
        )

        private val YOUTUBE_SCREEN_IDS = setOf(
            "com.google.android.youtube:id/shorts_player_container",
            "com.google.android.youtube:id/shorts_video_cell",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_player_underlay",
            "com.google.android.youtube:id/shorts_container",
            "com.google.android.youtube:id/shorts_fragment_container",
            "com.google.android.youtube:id/reel_recycler"
        )

        // ── SNAPCHAT DISABLED ─────────────────────────────────────────────────────
        // Uncomment the block below to re-enable Snapchat Spotlight tracking.
        /*
        private val SNAPCHAT_SCREEN_IDS = setOf(
            "com.snapchat.android:id/spotlight_container",
            "com.snapchat.android:id/opera_viewer",
            "com.snapchat.android:id/spotlight_feed_container",
            "com.snapchat.android:id/spotlight_fragment_container",
            "com.snapchat.android:id/spotlight_page",
            "com.snapchat.android:id/spotlight_view_pager",
            "com.snapchat.android:id/spotlight_post_container",
            "com.snapchat.android:id/spotlight_video_view",
            "com.snapchat.android:id/spotlight_player_view",
            "com.snapchat.android:id/spotlight_feed_item",
            "com.snapchat.android:id/spotlight_recycler_view",
            "com.snapchat.android:id/spotlight_list",
            "com.snapchat.android:id/spotlight_snap_view",
            "com.snapchat.android:id/spotlight_player_container",
            "com.snapchat.android:id/hemi_player_root",
            "com.snapchat.android:id/hemi_player_container",
            "com.snapchat.android:id/hemi_player_view",
            "com.snapchat.android:id/sc_player",
            "com.snapchat.android:id/sc_player_view",
            "com.snapchat.android:id/media_player_view",
            "com.snapchat.android:id/video_player_container",
            "com.snapchat.android:id/video_player_view",
            "com.snapchat.android:id/media_container",
            "com.snapchat.android:id/vertical_snap_container",
            "com.snapchat.android:id/snap_player_surface_view",
            "com.snapchat.android:id/snap_view",
            "com.snapchat.android:id/snap_video_view",
            "com.snapchat.android:id/snap_player_view",
            "com.snapchat.android:id/story_player_view",
            "com.snapchat.android:id/story_player_container",
            "com.snapchat.android:id/discover_feed_container"
        )
        private val SNAPCHAT_SPOTLIGHT_HINT_IDS = setOf(
            "com.snapchat.android:id/spotlight_nav_item",
            "com.snapchat.android:id/bottom_bar_item_spotlight",
            "com.snapchat.android:id/spotlight_tab",
            "com.snapchat.android:id/spotlight_icon",
            "com.snapchat.android:id/action_bar_spotlight"
        ) + SNAPCHAT_SCREEN_IDS
        */

        // ── TIKTOK DISABLED ──────────────────────────────────────────────────────
        // Uncomment the block below to re-enable TikTok tracking.
        /*
        private val TIKTOK_SCREEN_IDS = setOf(
            "com.zhiliaoapp.musically:id/view_pager",
            "com.zhiliaoapp.musically:id/video_play_content_layout",
            "com.zhiliaoapp.musically:id/videoview_preview"
        )
        */

        private val INSTAGRAM_NON_REEL_IDS = listOf(
            "com.instagram.android:id/direct_thread",
            "com.instagram.android:id/row_thread_message_content",
            "com.instagram.android:id/inbox_edit_text",
            "com.instagram.android:id/message_content_container",
            "com.instagram.android:id/composer_edittext",
            "com.instagram.android:id/thread_title",
            "com.instagram.android:id/inbox_search_bar",
            "com.instagram.android:id/direct_private_share_text"
        )

        private val YOUTUBE_NON_REEL_IDS = listOf(
            "com.google.android.youtube:id/results"
        )

        // TIKTOK_NON_REEL_IDS — DISABLED
        // private val TIKTOK_NON_REEL_IDS = listOf(
        //     "com.zhiliaoapp.musically:id/profile_page_fragment",
        //     "com.zhiliaoapp.musically:id/user_profile",
        //     "com.zhiliaoapp.musically:id/inbox_fragment",
        //     "com.zhiliaoapp.musically:id/chat_list"
        // )

        // SNAPCHAT_NON_REEL_IDS — DISABLED
        // private val SNAPCHAT_NON_REEL_IDS = listOf(
        //     "com.snapchat.android:id/camera_page",
        //     "com.snapchat.android:id/chat_page",
        //     "com.snapchat.android:id/friends_page",
        //     "com.snapchat.android:id/map_page"
        // )

        private val INSTAGRAM_SCROLL_CLASSES = setOf(
            "androidx.recyclerview.widget.RecyclerView",
            "androidx.viewpager2.widget.ViewPager2",
            "android.support.v7.widget.RecyclerView",
            "com.instagram.common.ui.widget.reboundviewpager.ReboundViewPager"
        )

        private val YOUTUBE_SCROLL_CLASSES = setOf(
            "androidx.recyclerview.widget.RecyclerView",
            "androidx.viewpager2.widget.ViewPager2",
            "com.google.android.apps.youtube.app.ui.viewpager.SquishableViewPager2"
        )

        // SNAPCHAT_SCROLL_CLASSES — DISABLED
        // private val SNAPCHAT_SCROLL_CLASSES = setOf(
        //     "androidx.recyclerview.widget.RecyclerView", "androidx.viewpager2.widget.ViewPager2",
        //     "androidx.viewpager.widget.ViewPager", "android.view.View", "android.view.ViewGroup",
        //     "android.widget.FrameLayout", "com.snap.mushroom", "com.snap.ui",
        //     "SnapViewPager", "SpotlightFeedView", "HemiPlayerView"
        // )

        // TIKTOK_SCROLL_CLASSES — DISABLED
        // private val TIKTOK_SCROLL_CLASSES = setOf(
        //     "androidx.viewpager2.widget.ViewPager2",
        //     "androidx.recyclerview.widget.RecyclerView"
        // )

        private val COMMENT_ID_KEYWORDS = listOf(
            "comment", "reply", "replies", "comment_list", "bottom_sheet",
            "sheet", "reaction", "emoji", "like_list"
        )

        private val AD_KEYWORDS = listOf(
            "sponsored", "promoted", "install now", "shop now", "learn more", "apply now", "download"
        )

        // FIX E: App-specific view IDs that only appear when the current reel is a sponsored ad.
        // These IDs are in SIBLING nodes (not children of the scroll source), so the old
        // isSponsoredAd(sourceNode) approach could never find them.
        private val INSTAGRAM_SPONSORED_IDS = setOf(
            "com.instagram.android:id/sponsored_label_container",
            "com.instagram.android:id/ads_info_icon",
            "com.instagram.android:id/ugc_ad_label",
            "com.instagram.android:id/ad_badge_container",
            "com.instagram.android:id/clips_ads_ufi_root",
            "com.instagram.android:id/clips_ad_badge"
        )

        // TIKTOK_SPONSORED_IDS — DISABLED
        // private val TIKTOK_SPONSORED_IDS = setOf(
        //     "com.zhiliaoapp.musically:id/promoted_label",
        //     "com.zhiliaoapp.musically:id/ads_label",
        //     "com.zhiliaoapp.musically:id/ad_badge"
        // )

        fun getScreenIdsForPackage(pkg: String): Set<String> = when (pkg) {
            "com.instagram.android"      -> INSTAGRAM_SCREEN_IDS
            "com.google.android.youtube" -> YOUTUBE_SCREEN_IDS
            // "com.snapchat.android"    -> SNAPCHAT_SCREEN_IDS   // DISABLED
            // "com.zhiliaoapp.musically"-> TIKTOK_SCREEN_IDS     // DISABLED
            else -> emptySet()
        }

        fun getScrollClassesForPackage(pkg: String): Set<String> = when (pkg) {
            "com.instagram.android"      -> INSTAGRAM_SCROLL_CLASSES
            "com.google.android.youtube" -> YOUTUBE_SCROLL_CLASSES
            // "com.snapchat.android"    -> SNAPCHAT_SCROLL_CLASSES  // DISABLED
            // "com.zhiliaoapp.musically"-> TIKTOK_SCROLL_CLASSES    // DISABLED
            else -> emptySet()
        }
    }

    // ─── State ─────────────────────────────────────────────────────────────────
    private var lastScrollTime       = 0L
    private var currentReelIndex     = -1
    private var sessionStartTime     = 0L
    private var lastSurfaceCheckTime = 0L
    private var lastSurfaceCheckResult = false
    var isInSession = false
        private set

    private var prevLastScrollTime   = 0L

    // FIX E: Cached result of the last full-screen sponsored ad check.
    // Updated by isReelScreenVisible() on each tree scan; read by isValidReelScroll().
    // Main-thread-only (both callers run on the accessibility main thread).
    private var isCurrentReelSponsored = false

    // ─── Main Entry: Scroll Validation ────────────────────────────────────────

    fun isValidReelScroll(event: AccessibilityEvent, activePackage: String): Boolean {
        try {
            val className = event.className?.toString() ?: ""
            val now = System.currentTimeMillis()
            val deltaMs = now - lastScrollTime

            // ── 0. Hard Lockout (450ms) to prevent double counting on drag/settle ──
            if (deltaMs < SCROLL_DEBOUNCE_MS) {
                Log.d(TAG, "⏸ Hard lockout active (${deltaMs}ms < ${SCROLL_DEBOUNCE_MS}ms) — ignoring duplicate scroll")
                return false
            }

            Log.d(TAG, "Scroll event received: deltaMs=${deltaMs}ms, pkg=$activePackage, class=$className")

            // ── 1. Class Whitelist Check ─────────────────────────────────────
            val allowedClasses = getScrollClassesForPackage(activePackage)
            val isAllowedClass = if (allowedClasses.isNotEmpty()) {
                allowedClasses.any { className.contains(it, ignoreCase = true) } ||
                className.contains("RecyclerView", ignoreCase = true) ||
                className.contains("ViewPager", ignoreCase = true)
            } else {
                className.contains("RecyclerView", ignoreCase = true) ||
                className.contains("ViewPager", ignoreCase = true)
            }

            if (!isAllowedClass) {
                Log.d(TAG, "❌ Class '$className' not in allowed reel scroll list for $activePackage")
                return false
            }

            // ── 2. Reject clearly horizontal swipes (Android P+) ─────────────
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val dx = event.scrollDeltaX
                val dy = event.scrollDeltaY
                if (dx != 0 && Math.abs(dx) > Math.abs(dy) * 2) {
                    Log.d(TAG, "⟷ Horizontal swipe rejected (dx=$dx, dy=$dy)")
                    return false
                }
            }

            // ── 3. Source node filters: comment section + small area + ADS ───
            val sourceNode = try { event.source } catch (_: Exception) { null }
            if (sourceNode != null) {
                try {
                    val viewId = sourceNode.viewIdResourceName ?: ""

                    if (COMMENT_ID_KEYWORDS.any { viewId.contains(it, ignoreCase = true) }) {
                        Log.d(TAG, "💬 Comment section scroll rejected (id=$viewId)")
                        safeRecycle(sourceNode)
                        return false
                    }

                    // FIX E: Check the CACHED root-level sponsored flag (set during last tree scan
                    // by isReelScreenVisible). The old approach of scanning sourceNode children
                    // couldn't find Instagram's "Sponsored" label which is a SIBLING, not a child
                    // of the RecyclerView/ViewPager scroll source.
                    if (isCurrentReelSponsored) {
                        Log.d(TAG, "📢 Sponsored/Ad reel detected (root-scan cache) -> IGNORING count (+0)")
                        safeRecycle(sourceNode)
                        return false
                    }

                    val bounds = Rect()
                    sourceNode.getBoundsInScreen(bounds)
                    val nodeHeight    = bounds.bottom - bounds.top
                    // FIX: Use getDisplayHeight() instead of Resources.getSystem() for accurate
                    // display dimensions in split-screen / foldable multi-window scenarios.
                    val displayHeight = getDisplayHeight()
                    val smallAreaCutoff = displayHeight * SMALL_AREA_SCREEN_PERCENT / 100

                    if (nodeHeight in 1 until smallAreaCutoff) {
                        Log.d(TAG, "📏 Small area rejected (h=$nodeHeight displayH=$displayHeight cutoff=$smallAreaCutoff)")
                        safeRecycle(sourceNode)
                        return false
                    }
                } catch (_: Exception) {}
                safeRecycle(sourceNode)
            }

            // ── 4. Exact Index & Page Change Tracking ────────────────────────
            val fromIndex = event.fromIndex
            val toIndex   = event.toIndex
            val targetIndex = if (toIndex != -1) toIndex else fromIndex

            if (targetIndex != -1) {
                if (currentReelIndex == -1) {
                    currentReelIndex = targetIndex
                    Log.d(TAG, "📍 Initial reel page index set: $targetIndex — counting as first reel")
                    prevLastScrollTime = lastScrollTime
                    lastScrollTime = now
                    updateSession(now, activePackage)
                    return true
                }

                if (targetIndex == currentReelIndex) {
                    Log.d(TAG, "🔁 Scroll event for current reel page index $currentReelIndex ignored (drag/settle)")
                    return false
                }

                // Target index changed -> Genuine new Reel!
                val prevIndex = currentReelIndex
                currentReelIndex = targetIndex
                prevLastScrollTime = lastScrollTime
                lastScrollTime = now
                Log.d(TAG, "🎯 New Reel Page transition confirmed: $prevIndex -> $targetIndex")
                updateSession(now, activePackage)
                return true
            }

            prevLastScrollTime = lastScrollTime
            lastScrollTime = now
            updateSession(now, activePackage)
            Log.d(TAG, "✅ Valid reel swipe gesture validated! [pkg=$activePackage class=$className deltaMs=${deltaMs}ms]")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "isValidReelScroll error: ${e.message}", e)
            return false
        }
    }

    // FIX E: Root-level sponsored content scan used by isReelScreenVisible().
    // Traverses the ENTIRE visible tree (depth 12) looking for:
    //  a) Instagram/TikTok-specific sponsored view IDs (fast, exact match)
    //  b) AD_KEYWORDS in text/contentDescription (broader fallback)
    // This is called ONCE per tree scan (150ms throttled), not per scroll event.
    private fun isSponsoredRootScan(
        node: AccessibilityNodeInfo?,
        activePackage: String,
        depth: Int = 0
    ): Boolean {
        if (node == null || depth > 12) return false
        try {
            val viewId = node.viewIdResourceName ?: ""

            // Fast path: app-specific sponsored IDs (most reliable)
            val sponsoredIds = when (activePackage) {
                "com.instagram.android"    -> INSTAGRAM_SPONSORED_IDS
                // "com.zhiliaoapp.musically" -> TIKTOK_SPONSORED_IDS // DISABLED
                else                        -> emptySet()
            }
            if (sponsoredIds.any { viewId.contains(it, ignoreCase = true) || viewId == it }) return true

            // Slow path: text/desc keyword scan
            val txt  = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (AD_KEYWORDS.any { txt == it || desc == it }) return true
            // "sponsored" text exact match is very reliable; partial match for longer keywords
            if (txt == "sponsored" || desc == "sponsored") return true

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = isSponsoredRootScan(child, activePackage, depth + 1)
                safeRecycle(child)
                if (found) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun updateSession(now: Long, activePackage: String) {
        val idleSinceMs = now - prevLastScrollTime
        if (!isInSession || idleSinceMs > SESSION_TIMEOUT_MS) {
            isInSession = true
            sessionStartTime = now
            Log.d(TAG, "📺 Reel session active ($activePackage) [idleSince=${idleSinceMs}ms]")
        }
    }

    // ─── Screen Visibility Detection ──────────────────────────────────────────

    fun isReelScreenVisible(rootNode: AccessibilityNodeInfo, activePackage: String): Boolean {
        try {
            // 1. Reject active DM/Chat screen first
            if (isNonReelScreen(rootNode, activePackage)) {
                Log.d(TAG, "🚫 Non-reel DM/Chat screen detected -> Reel mode FALSE")
                isCurrentReelSponsored = false  // FIX E: reset on non-reel
                return false
            }

            // 2. Check Reel Screen View IDs
            val viewIds = getScreenIdsForPackage(activePackage)
            for (id in viewIds) {
                try {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    if (!nodes.isNullOrEmpty()) {
                        var found = false
                        for (node in nodes) {
                            try {
                                if (!found && isLargeEnoughForReel(node)) {
                                    found = true
                                }
                            } catch (_: Exception) {}
                            safeRecycle(node)
                        }
                        if (found) {
                            Log.d(TAG, "✅ Reel screen confirmed by view ID: $id")
                            // FIX E: Now that we know it's a reel screen, do the full-root
                            // sponsored scan ONCE and cache the result. isValidReelScroll() will
                            // check isCurrentReelSponsored instead of re-scanning sourceNode children.
                            isCurrentReelSponsored = isSponsoredRootScan(rootNode, activePackage)
                            if (isCurrentReelSponsored) {
                                Log.d(TAG, "📢 Root-scan found sponsored ad on current reel screen")
                            }
                            return true
                        }
                    }
                } catch (_: Exception) {}

            }

            // 3. Check Fullscreen Video Surface / PlayerView (>= 50% height)
            val isSurfaceFound = findFullscreenSurface(rootNode)
            if (isSurfaceFound) {
                Log.d(TAG, "✅ Reel screen confirmed by video SurfaceView/TextureView")
                return true
            }

            // 4. Check Reel Scroll Container (ViewPager / RecyclerView >= 50% height)
            val isScrollContainerFound = findReelScrollContainer(rootNode, activePackage)
            if (isScrollContainerFound) {
                Log.d(TAG, "✅ Reel screen confirmed by scroll container (ViewPager/RecyclerView)")
                return true
            }

        } catch (e: Exception) {
            Log.e(TAG, "isReelScreenVisible error: ${e.message}")
        }
        return false
    }

    private fun findReelScrollContainer(node: AccessibilityNodeInfo, activePackage: String, depth: Int = 0): Boolean {
        if (depth > 8) return false
        try {
            if (!node.isVisibleToUser) return false
            val className = node.className?.toString() ?: ""
            val allowedClasses = getScrollClassesForPackage(activePackage)
            val isScrollClass = allowedClasses.any { className.contains(it, ignoreCase = true) }

            if (isScrollClass) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val h = bounds.bottom - bounds.top
                val w = bounds.right  - bounds.left
                // FIX: Use getDisplayHeight/Width() for accurate split-screen / foldable support.
                val displayH = getDisplayHeight()
                val displayW = getDisplayWidth()

                if (h >= displayH * 0.50f && w >= displayW * 0.50f) {
                    return true
                }
            }

            // NEW BUG A FIX: Always recycle child BEFORE returning true, otherwise
            // remaining children (i+1 .. childCount-1) are never recycled → memory leak.
            // Same pattern as BUG 10 fix in findFullscreenSurface.
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = try { findReelScrollContainer(child, activePackage, depth + 1) } catch (_: Exception) { false }
                safeRecycle(child)
                if (found) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun isNonReelScreen(rootNode: AccessibilityNodeInfo, activePackage: String): Boolean {
        // Special case for Snapchat: Snapchat's ViewPager keeps ALL tab pages in memory — DISABLED
        /*
        if (activePackage == "com.snapchat.android") {
            // Fast path: exact spotlight containers
            for (id in SNAPCHAT_SCREEN_IDS) {
                try {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    if (!nodes.isNullOrEmpty()) {
                        var foundVisible = false
                        for (n in nodes) {
                            if (n.isVisibleToUser) foundVisible = true
                            safeRecycle(n)
                        }
                        if (foundVisible) {
                            Log.d(TAG, "👻 Snapchat Spotlight screen active ($id) → Bypassing non-reel tab check")
                            return false
                        }
                    }
                } catch (_: Exception) {}
            }
            // Hint path: broader spotlight indicator IDs (nav bar items, tab markers)
            for (id in SNAPCHAT_SPOTLIGHT_HINT_IDS) {
                try {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    if (!nodes.isNullOrEmpty()) {
                        var anyFound = false
                        for (n in nodes) { anyFound = true; safeRecycle(n) }
                        if (anyFound) {
                            Log.d(TAG, "👻 Snapchat Spotlight HINT ID found ($id) → Bypassing non-reel tab check")
                            return false
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        */

        val nonReelIds = when (activePackage) {
            "com.instagram.android"      -> INSTAGRAM_NON_REEL_IDS
            "com.google.android.youtube" -> YOUTUBE_NON_REEL_IDS
            // "com.zhiliaoapp.musically"   -> TIKTOK_NON_REEL_IDS   // DISABLED
            // "com.snapchat.android"       -> SNAPCHAT_NON_REEL_IDS // DISABLED
            else -> emptyList()
        }

        for (id in nonReelIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    var foundVisible = false
                    for (n in nodes) {
                        if (n.isVisibleToUser) foundVisible = true
                        safeRecycle(n)
                    }
                    if (foundVisible) {
                        Log.d(TAG, "🚫 Non-reel view ID found: $id -> Reel mode FALSE")
                        return true
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun isLargeEnoughForReel(node: AccessibilityNodeInfo): Boolean {
        return try {
            if (!node.isVisibleToUser) return false
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val h = bounds.bottom - bounds.top
            val w = bounds.right  - bounds.left
            // FIX: Use getDisplayHeight/Width() helpers for accurate multi-window support.
            val displayH = getDisplayHeight()
            val displayW = getDisplayWidth()

            val isVisibleOnScreen = bounds.top < displayH && bounds.bottom > 0 && bounds.left < displayW && bounds.right > 0
            if (!isVisibleOnScreen) return false

            h >= MIN_VIDEO_HEIGHT_PX && w >= MIN_VIDEO_WIDTH_PX
        } catch (_: Exception) { false }
    }

    private fun findFullscreenSurface(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 10) return false
        try {
            if (!node.isVisibleToUser) return false

            val className = node.className?.toString() ?: ""
            val isVideoSurface = className.contains("SurfaceView", ignoreCase = true) ||
                    className.contains("TextureView", ignoreCase = true) ||
                    className.contains("VideoView", ignoreCase = true) ||
                    className.contains("PlayerView", ignoreCase = true) ||
                    className.contains("ExoPlayer", ignoreCase = true)

            if (isVideoSurface) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val h = bounds.bottom - bounds.top
                val w = bounds.right  - bounds.left
                // FIX: Use getDisplayHeight/Width() helpers for accurate multi-window support.
                val displayH = getDisplayHeight()
                val displayW = getDisplayWidth()

                val isVisibleOnScreen = bounds.top < displayH && bounds.bottom > 0 && bounds.left < displayW && bounds.right > 0
                if (isVisibleOnScreen && h >= displayH * 0.50f && w >= displayW * 0.50f) {
                    Log.d(TAG, "  Fullscreen Reel Surface found: $className (${w}x${h})")
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = try { findFullscreenSurface(child, depth + 1) } catch (_: Exception) { false }
                safeRecycle(child)
                if (found) return true
            }
        } catch (_: Exception) {}
        return false
    }

    // ─── Session Management ────────────────────────────────────────────────────

    fun resetSurfaceCache() {
        lastSurfaceCheckTime = 0L
        lastSurfaceCheckResult = false
    }

    fun endSession() {
        isInSession = false
        currentReelIndex = -1
        prevLastScrollTime = 0L
        lastSurfaceCheckTime = 0L
        lastSurfaceCheckResult = false
        Log.d(TAG, "Session ended — currentReelIndex reset to -1")
    }

    fun getSessionDurationSeconds(): Long =
        if (isInSession) (System.currentTimeMillis() - sessionStartTime) / 1000L else 0L

    fun isSessionExpired(): Boolean =
        isInSession && (System.currentTimeMillis() - lastScrollTime > SESSION_TIMEOUT_MS)

    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        try { node?.recycle() } catch (_: Exception) {}
    }
}