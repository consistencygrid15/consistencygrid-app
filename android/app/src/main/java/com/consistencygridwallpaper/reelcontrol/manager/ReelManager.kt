package com.consistencygridwallpaper.reelcontrol.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.consistencygridwallpaper.reelcontrol.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * ReelManager — Single source of truth for all reel tracking state.
 *
 * KEY RULE: Always reload counts from prefs on setActivePackage().
 * Never trust in-memory count alone — prefs are the ground truth.
 *
 * LOCKING RULE: NEVER call prefs.* while holding ReelManager.lock.
 * prefs has its own ReentrantReadWriteLock. Calling prefs inside our
 * write lock creates a nested-lock dependency that can deadlock.
 * Pattern: read prefs → acquire lock → update StateFlows → release lock.
 */
class ReelManager private constructor(context: Context) {

    private val prefs = PreferencesManager(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = ReentrantReadWriteLock()

    companion object {
        private const val TAG = "ReelManager"

        @Volatile private var instance: ReelManager? = null

        fun getInstance(ctx: Context): ReelManager =
            instance ?: synchronized(this) {
                instance ?: ReelManager(ctx.applicationContext).also { instance = it }
            }

        /** Call on service reconnect to reset in-memory state without breaking existing references.
         *
         * BUG-RM FIX: The old implementation called `instance = null`, which caused a stale
         * reference problem:
         *  1. UI/Repository cached `ReelManager.getInstance(appCtx)` → holds reference to InstanceA
         *  2. Service reconnects → invalidate() nulls InstanceA → getInstance() creates InstanceB
         *  3. Service uses InstanceB; UI/Repository still use InstanceA (zombie)
         *  4. Settings changes from UI go to InstanceA → service (InstanceB) never sees them!
         *
         * New behavior: Reset the in-memory state of the EXISTING instance.
         *  - setActivePackage() will reload fresh counts from prefs on the next foreground event
         *  - If the process was genuinely killed+restarted, instance IS null in the new process;
         *    getInstance() then creates a fresh one normally.
         */
        fun invalidate() {
            instance?.let { rm ->
                rm.lock.writeLock().lock()
                try {
                    rm._limitReached.value = false
                    rm._inReelMode.value   = false
                    rm.activePackage       = ""
                    rm.limitSnoozedUntilMs = 0L
                    Log.d(TAG, "🔄 ReelManager state reset (invalidate) — singleton instance preserved for existing references")
                } finally {
                    rm.lock.writeLock().unlock()
                }
            } ?: Log.d(TAG, "🔄 ReelManager.invalidate(): no instance — fresh instance will be created on next getInstance()")
        }

    }

    // ─── Observable State ─────────────────────────────────────────────────────
    private var activePackage = ""

    private val _reelCount  = MutableStateFlow(0)
    val reelCount: StateFlow<Int> = _reelCount

    private val _watchTime  = MutableStateFlow(0L) // seconds
    val watchTime: StateFlow<Long> = _watchTime

    private val _inReelMode = MutableStateFlow(false)
    val inReelMode: StateFlow<Boolean> = _inReelMode

    private val _limitReached = MutableStateFlow(false)
    val limitReached: StateFlow<Boolean> = _limitReached

    private val _blockMode = MutableStateFlow(false)
    val blockMode: StateFlow<Boolean> = _blockMode

    // ─── Cached Limits (updated at setActivePackage — no prefs calls inside lock) ──
    // DEADLOCK FIX: getLimit() / getTimeLimit() previously called prefs.getReelLimit()
    // inside the read lock, creating a ReelManager.lock → PreferencesManager.lock
    // nested-lock dependency. Now these are simple in-memory reads under our lock only.
    @Volatile private var cachedReelLimit: Int = 30
    @Volatile private var cachedTimeLimit: Int = 20

    // ─── Session Tracking ─────────────────────────────────────────────────────
    private var sessionStartMs = 0L
    private var timerRunnable: Runnable? = null

    // ─── App Switch ───────────────────────────────────────────────────────────
    /**
     * Called every time the foreground package changes.
     *
     * CRITICAL FIX: We ALWAYS reload from prefs when pkg is non-empty,
     * even if pkg == activePackage. This ensures counts survive:
     *   - Phone restart (process died, singleton is fresh)
     *   - OEM service reconnect (singleton survived but prefs may be ahead)
     *   - Any case where in-memory state drifted from disk state
     */
    fun setActivePackage(pkg: String) {
        Log.d(TAG, "setActivePackage: '$activePackage' → '$pkg'")

        // Read prefs OUTSIDE our lock (prefs has its own lock)
        val newCount: Int
        val newWatchTime: Long
        val newBlockMode: Boolean
        val newReelLimit: Int
        val newTimeLimit: Int
        if (pkg.isEmpty()) {
            newCount = 0; newWatchTime = 0L; newBlockMode = false
            newReelLimit = 30; newTimeLimit = 20
        } else {
            newCount      = prefs.getTodayReelCount(pkg)
            newWatchTime  = prefs.getTodayWatchTimeSeconds(pkg)
            newBlockMode  = prefs.isBlockModeEnabled
            newReelLimit  = prefs.getReelLimit(pkg)
            newTimeLimit  = prefs.getTimeLimit(pkg)
        }

        lock.writeLock().lock()
        try {
            activePackage    = pkg
            cachedReelLimit  = newReelLimit
            cachedTimeLimit  = newTimeLimit
            if (pkg.isEmpty()) {
                _reelCount.value    = 0
                _watchTime.value    = 0L
                _limitReached.value = false
            } else {
                _reelCount.value = newCount
                _watchTime.value = newWatchTime
                _blockMode.value = newBlockMode
                checkLimitsLocked(newReelLimit, newTimeLimit)
                Log.d(TAG, "Loaded from prefs → reels=${_reelCount.value} time=${_watchTime.value}s for $pkg (limit=$newReelLimit reels / ${newTimeLimit}m)")
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    // ─── Enter / Exit Reel Mode ───────────────────────────────────────────────
    fun enterReelMode() {
        // Read prefs outside our lock first
        val pkg: String
        lock.readLock().lock()
        try { pkg = activePackage } finally { lock.readLock().unlock() }

        if (pkg.isEmpty()) return
        val isEnabled = prefs.isAppEnabled(pkg)
        if (!isEnabled) return

        val freshCount     = prefs.getTodayReelCount(pkg)
        val freshWatchTime = prefs.getTodayWatchTimeSeconds(pkg)
        val blockEnabled   = prefs.isBlockModeEnabled
        val freshReelLimit = prefs.getReelLimit(pkg)
        val freshTimeLimit = prefs.getTimeLimit(pkg)

        lock.writeLock().lock()
        try {
            if (_inReelMode.value || activePackage.isEmpty()) return
            // Update from freshly-fetched prefs values
            _reelCount.value  = freshCount
            _watchTime.value  = freshWatchTime
            _inReelMode.value = true
            _blockMode.value  = blockEnabled
            cachedReelLimit   = freshReelLimit
            cachedTimeLimit   = freshTimeLimit
            sessionStartMs    = System.currentTimeMillis()
            startHeartbeat()
            Log.d(TAG, "▶ Entered reel mode: $activePackage | count=${_reelCount.value}")
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun exitReelMode() {
        // Step 1: Read elapsed time and package under write lock, then release
        val pkg: String
        val elapsed: Long
        val reelLimit: Int
        val timeLimit: Int
        lock.writeLock().lock()
        try {
            if (!_inReelMode.value) return
            _inReelMode.value = false
            stopHeartbeat()
            elapsed   = (System.currentTimeMillis() - sessionStartMs) / 1000L
            pkg       = activePackage
            reelLimit = cachedReelLimit
            timeLimit = cachedTimeLimit
        } finally {
            lock.writeLock().unlock()
        }

        // Step 2: Write to prefs OUTSIDE our lock (prefs has its own lock)
        // This prevents nested-lock deadlock between ReelManager.lock and PreferencesManager.lock.
        var updatedWatchTime = 0L
        if (pkg.isNotEmpty() && elapsed > 0) {
            prefs.addWatchTime(pkg, elapsed)
            updatedWatchTime = prefs.getTodayWatchTimeSeconds(pkg)
        }

        // Step 3: Re-acquire our lock to update StateFlow
        lock.writeLock().lock()
        try {
            if (pkg.isNotEmpty() && elapsed > 0) {
                _watchTime.value = updatedWatchTime
            }
            checkLimitsLocked(reelLimit, timeLimit)
            Log.d(TAG, "⏹ Exited reel mode after ${elapsed}s | total=${_watchTime.value}s")
        } finally {
            lock.writeLock().unlock()
        }
    }

    /** Persist current watch time while keeping the reel session active. */
    fun pauseSessionTiming() {
        val pkg: String
        val elapsed: Long
        val reelLimit: Int
        val timeLimit: Int

        lock.writeLock().lock()
        try {
            if (!_inReelMode.value || activePackage.isEmpty()) return
            pkg = activePackage
            elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000L
            sessionStartMs = System.currentTimeMillis()
            reelLimit = cachedReelLimit
            timeLimit = cachedTimeLimit
        } finally {
            lock.writeLock().unlock()
        }

        if (elapsed <= 0L) return

        prefs.addWatchTime(pkg, elapsed)
        val updatedWatchTime = prefs.getTodayWatchTimeSeconds(pkg)

        lock.writeLock().lock()
        try {
            if (_inReelMode.value && activePackage == pkg) {
                _watchTime.value = updatedWatchTime
                checkLimitsLocked(reelLimit, timeLimit)
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun onScrollDetected(pkg: String = "") {
        // Determine target package under read lock first
        val targetPkg: String
        lock.readLock().lock()
        try {
            targetPkg = if (pkg.isNotEmpty()) pkg else activePackage
        } finally {
            lock.readLock().unlock()
        }

        if (targetPkg.isEmpty()) {
            Log.w(TAG, "onScrollDetected ignored: empty package")
            return
        }

        // Check enabled & increment OUTSIDE our lock (prefs has its own lock)
        val isEnabled = prefs.isAppEnabled(targetPkg)
        if (!isEnabled) {
            Log.w(TAG, "onScrollDetected ignored: disabled package '$targetPkg'")
            return
        }
        val newCount  = prefs.incrementReelCount(targetPkg)

        // Update in-memory state under write lock (no prefs calls inside!)
        lock.writeLock().lock()
        try {
            if (activePackage != targetPkg) {
                Log.d(TAG, "onScrollDetected updating activePackage: '$activePackage' -> '$targetPkg'")
                activePackage = targetPkg
            }
            _reelCount.value = newCount
            checkLimitsLocked(cachedReelLimit, cachedTimeLimit)
            Log.d(TAG, "📜 Reel incremented: ${_reelCount.value} reels for $targetPkg")
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Resets in-memory count and watch time for the given package.
     * Called when a logout is detected (package data cleared).
     * Prefs are already reset by PreferencesManager.resetStatsForPackage().
     */
    fun resetCountForPackage(pkg: String) {
        lock.writeLock().lock()
        try {
            if (activePackage == pkg) {
                _reelCount.value    = 0
                _watchTime.value    = 0L
                _limitReached.value = false
            }
            Log.d(TAG, "🔄 In-memory stats reset for $pkg (logout)")
        } finally {
            lock.writeLock().unlock()
        }
    }

    // ─── Heartbeat Timer ──────────────────────────────────────────────────────
    private fun startHeartbeat() {
        stopHeartbeat()
        timerRunnable = object : Runnable {
            override fun run() {
                // Step 1: Read volatile state under lock — do NOT call prefs here!
                val pkg: String
                val sessionStartSnapshot: Long
                val inReel: Boolean
                val reelLimit: Int
                val timeLimit: Int
                lock.readLock().lock()
                try {
                    pkg                  = activePackage
                    sessionStartSnapshot = sessionStartMs
                    inReel               = _inReelMode.value
                    reelLimit            = cachedReelLimit
                    timeLimit            = cachedTimeLimit
                } finally {
                    lock.readLock().unlock()
                }

                if (pkg.isNotEmpty() && inReel) {
                    // Step 2: Call prefs OUTSIDE our lock (it has its own lock)
                    val sessionSec = (System.currentTimeMillis() - sessionStartSnapshot) / 1000L
                    val saved = prefs.getTodayWatchTimeSeconds(pkg)

                    // Step 3: Re-acquire write lock to update StateFlow
                    lock.writeLock().lock()
                    try {
                        // Re-check inReelMode in case exitReelMode() ran while we were in prefs
                        if (_inReelMode.value && activePackage == pkg) {
                            _watchTime.value = saved + sessionSec
                            checkLimitsLocked(reelLimit, timeLimit)
                        }
                    } finally {
                        lock.writeLock().unlock()
                    }
                }

                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopHeartbeat() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    @Volatile private var limitSnoozedUntilMs = 0L

    /**
     * Atomically increases reel limit (+5) and time limit (+10m) for the given package,
     * clears limitReached state, and refreshes active state.
     */
    fun increaseLimitForPackage(pkg: String, extraReels: Int = 5, extraTimeMins: Int = 10) {
        val targetPkg = if (pkg.isNotEmpty()) pkg else getActivePackage()
        if (targetPkg.isEmpty()) return

        val currentReelLimit = prefs.getReelLimit(targetPkg)
        val currentTimeLimit = prefs.getTimeLimit(targetPkg)

        val todayReels = prefs.getTodayReelCount(targetPkg)
        val todayTimeMins = (prefs.getTodayWatchTimeSeconds(targetPkg) / 60L).toInt()

        // Base new limits on whichever is higher: existing limit or today's current usage
        val baseReel = maxOf(currentReelLimit, todayReels)
        val baseTime = maxOf(currentTimeLimit, todayTimeMins)

        val newReelLimit = (if (baseReel > 0) baseReel else 30) + extraReels
        val newTimeLimit = (if (baseTime > 0) baseTime else 20) + extraTimeMins

        prefs.setReelLimit(targetPkg, newReelLimit)
        prefs.setTimeLimit(targetPkg, newTimeLimit)

        lock.writeLock().lock()
        try {
            limitSnoozedUntilMs = 0L
            activePackage = targetPkg
            cachedReelLimit = newReelLimit
            cachedTimeLimit = newTimeLimit
            _limitReached.value = false
            Log.d(TAG, "📈 Increased limit for $targetPkg: reels=$newReelLimit, time=${newTimeLimit}m")
        } finally {
            lock.writeLock().unlock()
        }
        refreshLimitsForPackage(targetPkg)
    }

    // ─── Dynamic Limit Refresh ────────────────────────────────────────────────
    /**
     * Call when limits or feature toggles change in UI / Repository.
     * Reloads limits from prefs, updates cached values, and re-evaluates limit status.
     */
    fun refreshLimitsForPackage(pkg: String = "") {
        val targetPkg = pkg.ifEmpty {
            lock.readLock().lock()
            try { activePackage } finally { lock.readLock().unlock() }
        }
        if (targetPkg.isEmpty()) return

        // FIX D: If the app or global feature is disabled, unconditionally clear the
        // limit-reached state. Previously, checkLimitsLocked() would still see
        // count >= limit and keep limitReached=true even after the user toggled the
        // app off — causing the popup to persist for a "disabled" app.
        val isGloballyEnabled = prefs.isFeatureEnabled
        val isAppEnabled       = prefs.isAppEnabled(targetPkg)
        if (!isGloballyEnabled || !isAppEnabled) {
            lock.writeLock().lock()
            try {
                _limitReached.value = false
                if (activePackage == targetPkg) {
                    _blockMode.value = false
                }
                Log.d(TAG, "🔕 Limits cleared for $targetPkg (appEnabled=$isAppEnabled, featureEnabled=$isGloballyEnabled)")
            } finally {
                lock.writeLock().unlock()
            }
            return
        }

        val freshCount     = prefs.getTodayReelCount(targetPkg)
        val freshWatchTime = prefs.getTodayWatchTimeSeconds(targetPkg)
        val freshReelLimit = prefs.getReelLimit(targetPkg)
        val freshTimeLimit = prefs.getTimeLimit(targetPkg)
        val blockEnabled   = prefs.isBlockModeEnabled

        lock.writeLock().lock()
        try {
            cachedReelLimit = freshReelLimit
            cachedTimeLimit = freshTimeLimit
            _blockMode.value = blockEnabled

            if (activePackage == targetPkg) {
                _reelCount.value = freshCount
                _watchTime.value = freshWatchTime
                checkLimitsLocked(freshReelLimit, freshTimeLimit)
            } else {
                // Re-evaluate limit for targetPkg when updated from settings screen
                val countExceeded = freshReelLimit > 0 && freshCount >= freshReelLimit
                val timeExceeded  = freshTimeLimit > 0 && (freshWatchTime / 60L) >= freshTimeLimit.toLong()
                _limitReached.value = countExceeded || timeExceeded
            }
            Log.d(TAG, "🔄 Refreshed limits for $targetPkg: reels=$freshCount/$freshReelLimit, time=${freshWatchTime / 60L}/${freshTimeLimit}m, limitReached=${_limitReached.value}")
        } finally {
            lock.writeLock().unlock()
        }
    }


    // ─── Limit Check ─────────────────────────────────────────────────────────
    /**
     * MUST be called while holding the write lock.
     * Accepts pre-fetched limit values so it never needs to call into prefs
     * (which would create a nested-lock deadlock: ReelManager.lock → PreferencesManager.lock).
     */
    private fun checkLimitsLocked(reelLimit: Int, timeLimit: Int) {
        if (activePackage.isEmpty()) { _limitReached.value = false; return }

        // If currently snoozed, do not flag limit reached
        if (System.currentTimeMillis() < limitSnoozedUntilMs) {
            _limitReached.value = false
            return
        }

        val countExceeded = reelLimit > 0 && _reelCount.value >= reelLimit
        val timeExceeded  = timeLimit > 0 && (_watchTime.value / 60L) >= timeLimit.toLong()

        val newLimitReached = countExceeded || timeExceeded
        if (newLimitReached != _limitReached.value) {
            _limitReached.value = newLimitReached
            Log.w(TAG, "⚠ Limit reached=$newLimitReached for $activePackage (reels=${_reelCount.value}/$reelLimit, time=${_watchTime.value / 60L}/${timeLimit}m)")
        }
    }

    // ─── Block Mode ───────────────────────────────────────────────────────────
    fun refreshBlockMode() {
        // DEADLOCK FIX: Read prefs OUTSIDE our lock first, then acquire write lock
        val blockEnabled = prefs.isBlockModeEnabled
        lock.writeLock().lock()
        try { _blockMode.value = blockEnabled } finally { lock.writeLock().unlock() }
    }

    // ─── Dismissals / Resets ──────────────────────────────────────────────────
    // R1 FIX: Unified snooze duration — 5 min across all paths:
    //   - dismissLimit()          → 5 min (was 30 min — gave unintended long free pass)
    //   - PopupManager breathing  → 5 min (unchanged)
    //   - BlockManager hold-unlock → 5 min (unchanged)
    // Users now get the SAME 5-min grace period regardless of how they dismissed.
    // Previously dismissing by tapping "Dismiss" (without holding breathing) gave 6x longer
    // snooze than doing the intentional mindful pause — backwards incentive.
    fun dismissLimit() {
        snoozeLimit(5 * 60 * 1000L) // 5-minute snooze (consistent with PopupManager & BlockManager)
    }

    fun clearSnooze() {
        lock.writeLock().lock()
        try {
            limitSnoozedUntilMs = 0L
            Log.d(TAG, "🧹 Limit snooze cleared (limitSnoozedUntilMs set to 0)")
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun snoozeLimit(durationMs: Long = 30 * 60 * 1000L) {
        lock.writeLock().lock()
        try {
            limitSnoozedUntilMs = System.currentTimeMillis() + durationMs
            _limitReached.value = false
            if (_inReelMode.value) sessionStartMs = System.currentTimeMillis()
            Log.d(TAG, "Snoozed limit for ${durationMs / 1000L}s")
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun resetProgress() {
        // DEADLOCK FIX: Call prefs OUTSIDE our lock, then re-acquire to update StateFlows.
        // Previously this called prefs.resetDailyProgress() + getTodayReelCount() INSIDE
        // the write lock — nested-lock risk.
        val pkg: String
        val inReel: Boolean
        lock.readLock().lock()
        try { pkg = activePackage; inReel = _inReelMode.value } finally { lock.readLock().unlock() }

        // Prefs calls outside any lock
        prefs.resetDailyProgress()
        val freshCount     = if (pkg.isNotEmpty()) prefs.getTodayReelCount(pkg)         else 0
        val freshWatchTime = if (pkg.isNotEmpty()) prefs.getTodayWatchTimeSeconds(pkg)  else 0L
        val freshReelLimit = if (pkg.isNotEmpty()) prefs.getReelLimit(pkg)              else 30
        val freshTimeLimit = if (pkg.isNotEmpty()) prefs.getTimeLimit(pkg)              else 20

        lock.writeLock().lock()
        try {
            _reelCount.value    = freshCount
            _watchTime.value    = freshWatchTime
            _limitReached.value = false
            cachedReelLimit     = freshReelLimit
            cachedTimeLimit     = freshTimeLimit
            if (inReel) sessionStartMs = System.currentTimeMillis()
        } finally {
            lock.writeLock().unlock()
        }
    }

    // ─── Accessors ────────────────────────────────────────────────────────────
    fun getActivePackage(): String {
        lock.readLock().lock(); try { return activePackage } finally { lock.readLock().unlock() }
    }

    /**
     * Returns the cached reel limit for the active package.
     * DEADLOCK FIX: No longer calls prefs.getReelLimit() inside the lock.
     * cachedReelLimit is updated atomically at setActivePackage() / enterReelMode().
     */
    fun getLimit(): Int {
        lock.readLock().lock(); try { return cachedReelLimit } finally { lock.readLock().unlock() }
    }

    /**
     * Returns the cached time limit (in minutes) for the active package.
     * DEADLOCK FIX: No longer calls prefs.getTimeLimit() inside the lock.
     */
    fun getTimeLimit(): Int {
        lock.readLock().lock(); try { return cachedTimeLimit } finally { lock.readLock().unlock() }
    }

    fun getStreak(): Int = prefs.noReelStreak
    fun isInReelMode(): Boolean {
        lock.readLock().lock(); try { return _inReelMode.value } finally { lock.readLock().unlock() }
    }
    fun getPrefs(): PreferencesManager = prefs
}
