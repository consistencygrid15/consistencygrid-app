package com.consistencygridwallpaper.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * AlarmPermissionReceiver
 *
 * Listens for SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (Android 12+).
 * When the user navigates to Special App Access and grants the exact alarm
 * permission, this receiver fires and re-schedules the midnight alarm —
 * otherwise the alarm would only be (re-)scheduled the next time the user
 * opens the app.
 */
class AlarmPermissionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmPermissionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") {
            Log.d(TAG, "Exact alarm permission state changed — checking & rescheduling if granted")
            ExactAlarmScheduler.rescheduleAfterPermissionGranted(context)
        }
    }
}
