package com.consistencygridwallpaper.reelcontrol.manager

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.reelcontrol.utils.TimeFormatter

/**
 * OverlayManager — Floating HUD using TYPE_ACCESSIBILITY_OVERLAY.
 */
class OverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // R2 FIX: Cache canDrawOverlays at construction time — avoids IPC call to
    // PackageManager on every show/update/create invocation (previously called inside
    // createLayoutParams() which runs on every show). Result is immutable for the process lifetime.
    private val canDrawOverlaysCache = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
        android.provider.Settings.canDrawOverlays(context)

    private var overlayView: View? = null
    private var tvReelCount: TextView? = null
    private var tvWatchTime: TextView? = null
    private var pbLimitProgress: ProgressBar? = null
    private var isVisible = false
    private var cleanupRunnable: Runnable? = null

    // BUG 5 FIX: Use dedicated token objects as identifiers for removeCallbacksAndMessages().
    // This lets hideImmediate() selectively cancel only the pending show/update/cleanup
    // runnables that BELONG to this manager — without nuking unrelated messages that may
    // have been posted to the same handler (e.g. a show() call queued right before hide).
    private val showToken   = Any()
    private val updateToken = Any()
    private val hideToken   = Any()

    // Track current progress for smooth animation
    private var currentProgress = 0

    private val prefs = context.applicationContext.getSharedPreferences("overlay_hud_prefs", Context.MODE_PRIVATE)

    private fun createLayoutParams(): WindowManager.LayoutParams {
        // R2 FIX: Use cached canDrawOverlays result instead of making an IPC call each time.
        val overlayType = if (canDrawOverlaysCache) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        // R3 FIX: Query fresh display metrics at layout time — density can change at runtime
        // on foldable phones or when toggling multi-window mode.
        val density = context.resources.displayMetrics.density
        val displayW = context.resources.displayMetrics.widthPixels
        val defaultX = maxOf(0, (displayW - (220 * density).toInt()) / 2)
        var savedX = prefs.getInt("hud_pos_x", defaultX)
        var savedY = prefs.getInt("hud_pos_y", (48 * density).toInt())

        // Sanity check: if saved position is off-screen, reset to default top-center
        if (savedX < 0 || savedX > displayW - 50) {
            savedX = defaultX
            savedY = (48 * density).toInt()
            prefs.edit().putInt("hud_pos_x", savedX).putInt("hud_pos_y", savedY).apply()
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    fun show(reelCount: Int, watchTimeSeconds: Long, reelLimit: Int = 30, timeLimitMins: Int = 20) {
        val doShow: () -> Unit = doShow@ {
            try {
                Log.d(TAG, "OverlayManager.show() called: count=$reelCount, watchTime=${watchTimeSeconds}s, limits=($reelLimit reels / $timeLimitMins mins)")
                
                val current = overlayView
                if (current != null) {
                    current.visibility = View.VISIBLE
                    current.alpha = 1f
                    tvReelCount = current.findViewById(R.id.tv_reel_count)
                    tvWatchTime = current.findViewById(R.id.tv_watch_time)
                    pbLimitProgress = current.findViewById(R.id.pb_limit_progress)
                    updateViewsDirect(current, reelCount, watchTimeSeconds, reelLimit, timeLimitMins)
                    isVisible = true
                    Log.d(TAG, "✅ Existing HUD overlay made VISIBLE at (${current.x}, ${current.y})")
                    return@doShow
                }

                val view = LayoutInflater.from(context).inflate(R.layout.overlay_hud, null)
                tvReelCount = view.findViewById(R.id.tv_reel_count)
                tvWatchTime = view.findViewById(R.id.tv_watch_time)
                pbLimitProgress = view.findViewById(R.id.pb_limit_progress)

                updateViewsDirect(view, reelCount, watchTimeSeconds, reelLimit, timeLimitMins)

                val params = createLayoutParams()

                // Touch Drag Listener to move the HUD pill safely around the screen
                var initialX = 0
                var initialY = 0
                var initialTouchX = 0f
                var initialTouchY = 0f
                view.setOnTouchListener { _, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val density = context.resources.displayMetrics.density
                            val displayW = context.resources.displayMetrics.widthPixels
                            val displayH = context.resources.displayMetrics.heightPixels
                            val viewW = if (view.width > 0) view.width else (220 * density).toInt()
                            val viewH = if (view.height > 0) view.height else (44 * density).toInt()

                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()

                            val newX = (initialX + dx).coerceIn(0, maxOf(0, displayW - viewW))
                            val newY = (initialY + dy).coerceIn((24 * density).toInt(), maxOf(0, displayH - viewH))

                            params.x = newX
                            params.y = newY
                            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            prefs.edit().putInt("hud_pos_x", params.x).putInt("hud_pos_y", params.y).apply()
                            true
                        }
                        else -> false
                    }
                }

                view.alpha = 1f
                view.visibility = View.VISIBLE
                try {
                    windowManager.addView(view, params)
                } catch (e: Exception) {
                    Log.w(TAG, "First overlay addView failed (${e.message}) — attempting fallback type")
                    params.type = if (params.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        else WindowManager.LayoutParams.TYPE_PHONE
                    }
                    windowManager.addView(view, params)
                }

                overlayView = view
                isVisible = true
                startCleanupLoop()
                Log.d(TAG, "✅ HUD Overlay created & attached to WindowManager at (${params.x}, ${params.y})")
            } catch (e: Exception) {
                Log.e(TAG, "❌ OverlayManager.show() error: ${e.message}", e)
                isVisible = false
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) doShow() else handler.postAtTime(doShow, showToken, android.os.SystemClock.uptimeMillis())
    }

    fun update(reelCount: Int, watchTimeSeconds: Long, reelLimit: Int = 30, timeLimitMins: Int = 20) {
        val doUpdate: () -> Unit = {
            val view = overlayView
            if (view != null) {
                view.visibility = View.VISIBLE
                view.alpha = 1f
                isVisible = true
                try {
                    updateViews(reelCount, watchTimeSeconds, reelLimit, timeLimitMins)
                } catch (e: Exception) {
                    Log.e(TAG, "OverlayManager.update() error: ${e.message}", e)
                }
            } else {
                show(reelCount, watchTimeSeconds, reelLimit, timeLimitMins)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) doUpdate() else handler.postAtTime(doUpdate, updateToken, android.os.SystemClock.uptimeMillis())
    }

    /**
     * C3 FIX: destroy() FULLY removes the overlay view from WindowManager.
     *
     * hide()/hideImmediate() only set visibility = GONE — the view stays attached
     * to the WindowManager window token. If the AccessibilityService is killed and
     * restarted by the OS (common on OEMs), the old view token becomes invalid but
     * remains in WindowManager's list → stuck overlay on screen until process dies.
     *
     * destroy() is called from ReelTrackingService.cleanup() on every service disconnect.
     */
    fun destroy() {
        handler.removeCallbacksAndMessages(showToken)
        handler.removeCallbacksAndMessages(updateToken)
        handler.removeCallbacksAndMessages(hideToken)
        stopCleanupLoop()
        val doDestroy: () -> Unit = {
            try {
                val view = overlayView
                overlayView = null
                tvReelCount = null
                tvWatchTime = null
                pbLimitProgress = null
                isVisible = false
                if (view != null) {
                    try {
                        windowManager.removeViewImmediate(view)
                        Log.d(TAG, "🗑 HUD Overlay DESTROYED — removed from WindowManager")
                    } catch (e: Exception) {
                        try { windowManager.removeView(view) } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "destroy() error: ${e.message}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) doDestroy() else handler.post(doDestroy)
    }

    fun hide() {
        hideImmediate()
    }

    fun hideImmediate() {
        handler.removeCallbacksAndMessages(showToken)
        handler.removeCallbacksAndMessages(updateToken)
        handler.removeCallbacksAndMessages(hideToken)

        val doHide: () -> Unit = {
            try {
                val view = overlayView
                if (view != null) {
                    view.visibility = View.GONE
                }
                isVisible = false
                Log.d(TAG, "🙈 HUD Overlay set to GONE")
            } catch (e: Exception) {
                Log.e(TAG, "❌ hideImmediate() error: ${e.message}", e)
                isVisible = false
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) doHide() else handler.postAtTime(doHide, hideToken, android.os.SystemClock.uptimeMillis())
    }

    fun isShowing() = isVisible

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun updateViews(
        reelCount: Int, watchTimeSeconds: Long, reelLimit: Int, timeLimitMins: Int
    ) {
        tvReelCount?.text = reelCount.toString()
        tvWatchTime?.text = TimeFormatter.format(watchTimeSeconds)

        val pct = calculateProgress(reelCount, watchTimeSeconds, reelLimit, timeLimitMins)
        val pb = pbLimitProgress ?: return
        val animator = ObjectAnimator.ofInt(pb, "progress", currentProgress, pct)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.start()
        currentProgress = pct

        tvReelCount?.setTextColor(
            if (pct >= 80) android.graphics.Color.parseColor("#EF4444")
            else android.graphics.Color.parseColor("#F97316")
        )
    }

    private fun updateViewsDirect(
        view: View, reelCount: Int, watchTimeSeconds: Long, reelLimit: Int, timeLimitMins: Int
    ) {
        val tvCount = view.findViewById<TextView>(R.id.tv_reel_count)
        val tvTime = view.findViewById<TextView>(R.id.tv_watch_time)
        val pb = view.findViewById<ProgressBar>(R.id.pb_limit_progress)

        tvCount?.text = reelCount.toString()
        tvTime?.text = TimeFormatter.format(watchTimeSeconds)

        val pct = calculateProgress(reelCount, watchTimeSeconds, reelLimit, timeLimitMins)
        pb?.progress = pct
        currentProgress = pct

        tvCount?.setTextColor(
            if (pct >= 80) android.graphics.Color.parseColor("#EF4444")
            else android.graphics.Color.parseColor("#F97316")
        )
    }

    private fun calculateProgress(
        reelCount: Int, watchTimeSeconds: Long, reelLimit: Int, timeLimitMins: Int
    ): Int {
        val countPct = if (reelLimit > 0) (reelCount * 100 / reelLimit) else 0
        val timePct  = if (timeLimitMins > 0) ((watchTimeSeconds / 60f) * 100 / timeLimitMins).toInt() else 0
        return maxOf(countPct, timePct).coerceIn(0, 100)
    }

    private fun startCleanupLoop() {
        stopCleanupLoop()
        cleanupRunnable = object : Runnable {
            override fun run() {
                try {
                    val view = overlayView
                    // M2 FIX: When cleanup loop detects a detached view, attempt to fully
                    // remove it from WindowManager instead of just nulling references.
                    // Previously, the view remained in WindowManager's list with an invalid
                    // token — causing stuck overlays and potential WindowManager leaks.
                    if (isVisible && view != null && !view.isAttachedToWindow) {
                        Log.w(TAG, "Cleanup loop: detached view detected → removing from WindowManager")
                        overlayView = null; tvReelCount = null; tvWatchTime = null; pbLimitProgress = null; isVisible = false
                        try { windowManager.removeView(view) } catch (_: Exception) {}
                        return
                    }
                } catch (e: Exception) { Log.e(TAG, "cleanupLoop error: ${e.message}") }
                if (isVisible) handler.postDelayed(this, 1000L)
            }
        }
        handler.postDelayed(cleanupRunnable!!, 1000L)
    }

    private fun stopCleanupLoop() {
        cleanupRunnable?.let { handler.removeCallbacks(it) }
        cleanupRunnable = null
    }
}

