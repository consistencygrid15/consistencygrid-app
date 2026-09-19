package com.consistencygridwallpaper.utils

import android.util.Log
import com.consistencygridwallpaper.BuildConfig

/**
 * AppLogger — Debug-only logging wrapper.
 *
 * In RELEASE builds, all log calls are no-ops.
 * In DEBUG builds, delegates to android.util.Log.
 *
 * NEVER log tokens, package names, API response bodies,
 * or any personally-identifiable information.
 */
object AppLogger {

    /** Log a debug message. Silenced in release builds. */
    @JvmStatic
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    /** Log a verbose message. Silenced in release builds. */
    @JvmStatic
    fun v(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.v(tag, msg)
    }

    /** Log a warning. Silenced in release builds. */
    @JvmStatic
    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.w(tag, msg)
    }

    /**
     * Log an error with an optional throwable.
     * In release builds, the message is silenced but
     * consider routing to a crash reporter (e.g. Crashlytics)
     * without including PII.
     */
    @JvmStatic
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.e(tag, msg, throwable)
            else Log.e(tag, msg)
        }
        // Production: add non-PII crash reporting here if needed
        // e.g. FirebaseCrashlytics.getInstance().recordException(throwable)
    }
}
