package com.consistencygridwallpaper.reelcontrol.manager

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import kotlin.random.Random
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.consistencygridwallpaper.R

/**
 * BlockManager — "Mindful Pause" experience.
 *
 * Redesigned features:
 * - Breathing/loading spinner animation while card appears
 * - Motivational quote (rotated randomly from a local list)
 * - No-reel streak badge
 * - Hold-to-unlock (5s press-and-hold) instead of instant 1-min tap
 * - Go Home button (orange) triggers performGlobalAction(HOME)
 * - Snooze: 5-minute window after successful hold-to-unlock
 */
class BlockManager(private val context: Context) {

    companion object {
        private const val TAG = "BlockManager"
        private const val HOLD_DURATION_MS = 5_000L
        private const val HOLD_TICK_MS = 50L
        private const val SNOOZE_DURATION_MS = 5 * 60 * 1_000L

        private val QUOTES = listOf(
            "\"The less you watch, the more you live.\"",
            "\"Your attention is your most valuable asset.\"",
            "\"Build, don't scroll.\"",
            "\"Consistency beats intensity every time.\"",
            "\"Your future self thanks you.\"",
            "\"Real life is happening outside your screen.\"",
            "\"Choose intentional, not habitual.\"",
            "\"Progress, not perfection.\"",
            "\"Your goals deserve your full focus.\"",
            "\"Make habits — don't let habits make you.\""
        )
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // CONCERN 16 FIX: Persist snooze state in SharedPreferences so it survives
    // service/process restarts and phone reboots. Previously snoozedUntilMs was
    // in-memory only — a service restart would reset the snooze silently.
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences("block_manager_state", Context.MODE_PRIVATE)

    private var blockView: View? = null

    // C5 FIX: @Volatile flag to prevent double-show race condition.
    // blockView is set inside handler.post{showMindfulPause()} which runs asynchronously.
    // If triggerBlock() is called twice before the handler runs (e.g. limit reached on two
    // rapid scroll events), both calls see blockView==null and both try windowManager.addView()
    // → WindowManager: View already has a parent crash.
    @Volatile private var isShowingPending = false

    // Snooze state — backed by SharedPreferences for persistence across restarts
    private var snoozedUntilMs: Long
        get()  = prefs.getLong("snooze_until_ms", 0L)
        set(v) = prefs.edit().putLong("snooze_until_ms", v).apply()

    // Hold-to-unlock state
    private var holdStartMs = 0L
    private var holdRunnable: Runnable? = null

    // BUG 4 FIX: Replaced single shared layoutParams instance with a factory method.
    // Reusing one LayoutParams object across addView() calls can cause
    // "View already added" crashes on rapid re-shows / race conditions.
    private fun createLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // BUG-BM FIX: Add FLAG_NOT_FOCUSABLE (same as PopupManager FIX-A).
        // Without it, the block overlay steals keyboard focus from the underlying app.
        // rootInActiveWindow then returns the block window instead of Instagram's,
        // causing the accessibility tree scan to miss reel view IDs and trigger hysteresis.
        // FIX-B (skip scan when block showing) is the primary guard, but this adds
        // belt+suspenders safety in case a future code path bypasses the FIX-B check.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }


    fun triggerBlock(isHardBlock: Boolean, appName: String, streak: Int = 0, onHome: () -> Unit) {
        if (System.currentTimeMillis() < snoozedUntilMs) {
            Log.d(TAG, "Block suppressed — within snooze window")
            return
        }
        if (isHardBlock) {
            Log.w(TAG, "HARD BLOCK: firing home for $appName")
            try { onHome() } catch (e: Exception) { Log.e(TAG, "home action failed", e) }
            return
        }
        // C5 FIX: Reject if already showing or pending — prevents double-addView crash.
        if (isShowingPending || blockView != null) return
        isShowingPending = true
        handler.post { showMindfulPause(appName, streak, onHome) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showMindfulPause(appName: String, streak: Int, onHome: () -> Unit) {
        if (blockView != null) { isShowingPending = false; return }

        try {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)

            // Fade in
            view.alpha = 0f

            // Populate views
            view.findViewById<TextView>(R.id.tv_block_subtitle)?.text =
                "You've chosen to stay focused.\nStep away from $appName."
            view.findViewById<TextView>(R.id.tv_block_quote)?.text =
                QUOTES[Random.nextInt(QUOTES.size)]
            view.findViewById<TextView>(R.id.tv_streak_badge)?.text =
                if (streak > 0) "🔥 $streak day clean streak — keep it alive!" else "🌱 Start your clean streak today"

            // Go Home button
            view.findViewById<Button>(R.id.btn_go_home)?.setOnClickListener {
                hide()
                try { onHome() } catch (e: Exception) { Log.e(TAG, "home action failed", e) }
            }

            // Hold-to-unlock
            val holdContainer = view.findViewById<FrameLayout>(R.id.fl_hold_unlock)
            val pbHold = view.findViewById<ProgressBar>(R.id.pb_hold_progress)

            holdContainer?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        holdStartMs = SystemClock.elapsedRealtime()
                        startHoldProgress(pbHold) {
                            // Hold complete — grant 5-min snooze
                            snoozedUntilMs = System.currentTimeMillis() + SNOOZE_DURATION_MS
                            Log.d(TAG, "Hold complete — 5-min snooze granted")
                            hide()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        cancelHold(pbHold)
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(view, createLayoutParams())
            view.animate().alpha(1f).setDuration(400).start()
            blockView = view
            isShowingPending = false
            Log.d(TAG, "Mindful pause shown for $appName")
        } catch (e: Exception) {
            Log.e(TAG, "showMindfulPause error: ${e.message}")
            blockView = null
            isShowingPending = false
        }
    }

    private fun startHoldProgress(pb: ProgressBar?, onComplete: () -> Unit) {
        cancelHold(pb)
        holdRunnable = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.elapsedRealtime() - holdStartMs
                val pct = ((elapsed * 100L) / HOLD_DURATION_MS).toInt().coerceIn(0, 100)
                pb?.progress = pct
                if (pct >= 100) {
                    onComplete()
                } else {
                    handler.postDelayed(this, HOLD_TICK_MS)
                }
            }
        }
        handler.post(holdRunnable!!)
    }

    private fun cancelHold(pb: ProgressBar?) {
        holdRunnable?.let { handler.removeCallbacks(it) }
        holdRunnable = null
        pb?.progress = 0
    }

    fun hide() {
        val doHide: () -> Unit = {
            try {
                holdRunnable?.let { handler.removeCallbacks(it) }
                holdRunnable = null
                val view = blockView
                blockView = null
                if (view != null) {
                    try {
                        windowManager.removeViewImmediate(view)
                        Log.d(TAG, "Block view removed immediately from WindowManager")
                    } catch (e: Exception) {
                        try {
                            windowManager.removeView(view)
                            Log.d(TAG, "Block view removed via removeView fallback")
                        } catch (e2: Exception) {
                            Log.w(TAG, "Block view removeView failed: ${e2.message}")
                        }
                    }
                }
                Log.d(TAG, "Block overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "hide() error: ${e.message}")
                blockView = null
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) doHide() else handler.post(doHide)
    }

    fun isShowing() = blockView != null || isShowingPending
    fun isSnoozed() = System.currentTimeMillis() < snoozedUntilMs
}
