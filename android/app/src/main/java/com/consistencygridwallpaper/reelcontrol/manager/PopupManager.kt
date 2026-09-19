package com.consistencygridwallpaper.reelcontrol.manager

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.consistencygridwallpaper.R

/**
 * PopupManager — Shows the reel limit reached screen using TYPE_ACCESSIBILITY_OVERLAY.
 * Safe Main-thread overlay management with a single firm "Return to Home Screen" action.
 *
 * FIX: holdRunnable promoted to class field so dismiss() can cancel a running
 * breathing animation. Previously, calling dismiss() while the user was holding the
 * breathing button left the runnable running — it would reach 100% and call
 * reelManager.snoozeLimit() even after the popup was already dismissed.
 */
class PopupManager(private val context: Context) {

    companion object {
        private const val TAG = "PopupManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var popupView: View? = null

    // C4 FIX: @Volatile flag to prevent double-show race condition.
    // popupView is set inside handler.post{} which runs asynchronously.
    // If showLimitPopup() is called twice before the handler runs,
    // both calls see popupView==null and both try windowManager.addView() → crash.
    // This flag is set synchronously BEFORE post() so the second call is rejected immediately.
    @Volatile private var isShowingPending = false

    // FIX: Promoted from local variable to class field so dismiss() can always cancel it.
    private var breathingRunnable: Runnable? = null

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val overlayType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            android.provider.Settings.canDrawOverlays(context)) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            // FIX A: FLAG_NOT_FOCUSABLE is CRITICAL here!
            // Without it, the popup steals keyboard focus from the underlying app (Instagram).
            // When focus is stolen, AccessibilityService.rootInActiveWindow returns the POPUP's
            // window instead of Instagram's — so reel view IDs (clips_video_container, etc.)
            // can't be found → hysteresis fires after 4 scans → exitReelMode() → popup dismissed.
            // This created an endless show-dismiss loop that users see as "popup nahi aata".
            // FLAG_NOT_FOCUSABLE: popup can't receive keyboard focus, but touch events still work
            // because FLAG_NOT_TOUCH_MODAL is also set (touches go to popup since it's fullscreen).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = android.view.Gravity.CENTER }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun showLimitPopup(
        reelManager: ReelManager,
        onGoHome: () -> Unit
    ) {
        // C4 FIX: Reject if already showing or pending — prevents double-addView crash.
        if (isShowingPending || popupView != null) return
        isShowingPending = true
        handler.post {
            if (popupView != null) { isShowingPending = false; return@post }

            try {
                val view = LayoutInflater.from(context).inflate(R.layout.popup_limit, null)

                view.findViewById<Button?>(R.id.btn_go_home)?.setOnClickListener {
                    dismiss()
                    try { onGoHome() } catch (e: Exception) { Log.e(TAG, "GoHome error: ${e.message}") }
                }

                // 10-Second Mindful Breathing Break Touch Handler
                val breathingContainer = view.findViewById<android.view.View>(R.id.fl_breathing_break)
                val pbBreathing = view.findViewById<ProgressBar>(R.id.pb_breathing_progress)
                val tvBreathingLabel = view.findViewById<TextView>(R.id.tv_breathing_label)

                var holdStartMs = 0L

                breathingContainer?.setOnTouchListener { _, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            holdStartMs = android.os.SystemClock.elapsedRealtime()
                            tvBreathingLabel?.text = "Breathe in deeply… 🌬️"
                            breathingRunnable = object : Runnable {
                                override fun run() {
                                    val elapsed = android.os.SystemClock.elapsedRealtime() - holdStartMs
                                    val pct = ((elapsed * 100L) / 10_000L).toInt().coerceIn(0, 100)
                                    pbBreathing?.progress = pct
                                    if (pct >= 50) tvBreathingLabel?.text = "Exhale slowly… 🧘"
                                    if (pct >= 100) {
                                        // 10s completed -> grant 5-min snooze
                                        reelManager.snoozeLimit(5 * 60 * 1000L)
                                        Log.d(TAG, "10-sec Mindful Breathing completed -> 5-min focus break granted")
                                        dismiss()
                                    } else {
                                        handler.postDelayed(this, 50L)
                                    }
                                }
                            }
                            handler.post(breathingRunnable!!)
                            true
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            cancelBreathing(pbBreathing, tvBreathingLabel)
                            true
                        }
                        else -> false
                    }
                }

                val params = createLayoutParams()
                try {
                    windowManager.addView(view, params)
                } catch (e: Exception) {
                    Log.w(TAG, "Limit popup first addView failed (${e.message}) — attempting fallback type")
                    params.type = if (params.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    } else {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        else WindowManager.LayoutParams.TYPE_PHONE
                    }
                    windowManager.addView(view, params)
                }
                popupView = view
                isShowingPending = false
                Log.d(TAG, "Limit popup shown with 10s Breathing Break option")
            } catch (e: Exception) {
                Log.e(TAG, "showLimitPopup error: ${e.message}")
                popupView = null
                isShowingPending = false
            }
        }
    }

    /**
     * Cancels the in-progress breathing animation and resets progress bar + label.
     */
    private fun cancelBreathing(pb: ProgressBar?, label: TextView?) {
        breathingRunnable?.let { handler.removeCallbacks(it) }
        breathingRunnable = null
        pb?.progress = 0
        label?.text = "🧘 Hold 10s for 5m Mindful Break"
    }

    fun dismiss() {
        val doDismiss: () -> Unit = {
            try {
                // FIX: Cancel any in-flight breathing runnable BEFORE removing the view.
                // Previously this was a local variable — dismiss() couldn't reach it,
                // causing snoozeLimit() to be called after the popup was already gone.
                breathingRunnable?.let { handler.removeCallbacks(it) }
                breathingRunnable = null

                val view = popupView
                popupView = null
                if (view != null) {
                    try {
                        windowManager.removeViewImmediate(view)
                        Log.d(TAG, "Popup removed immediately from WindowManager")
                    } catch (e: Exception) {
                        try {
                            windowManager.removeView(view)
                            Log.d(TAG, "Popup removed via removeView fallback")
                        } catch (e2: Exception) {
                            Log.w(TAG, "Popup removeView failed: ${e2.message}")
                        }
                    }
                }
                Log.d(TAG, "Popup dismissed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "dismiss() error: ${e.message}")
                popupView = null
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) doDismiss() else handler.post(doDismiss)
    }

    fun isShowing() = popupView != null || isShowingPending
}

