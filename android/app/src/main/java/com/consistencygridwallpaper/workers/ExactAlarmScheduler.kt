package com.consistencygridwallpaper.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object ExactAlarmScheduler {
    private const val TAG = "ExactAlarmScheduler"
    private const val ALARM_REQUEST_CODE = 12000

    fun scheduleNextMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check permissions on Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms. Permission denied.")
                return
            }
        }

        val intent = Intent(context, MidnightReceiver::class.java)
        
        // Use FLAG_UPDATE_CURRENT to replace any existing alarm
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate next midnight milliseconds
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, 1) // Tomorrow
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetTimeMs = calendar.timeInMillis
        
        Log.d(TAG, "Scheduling Exact Alarm for ${calendar.time} ($targetTimeMs)")

        try {
            // Wake up the device and allow execution even in Doze mode
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTimeMs,
                pendingIntent
            )
            Log.d(TAG, "Exact alarm scheduled successfully!")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Failed to schedule exact alarm", e)
        }
    }

    fun cancelMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MidnightReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Exact alarm cancelled successfully!")
    }
}
