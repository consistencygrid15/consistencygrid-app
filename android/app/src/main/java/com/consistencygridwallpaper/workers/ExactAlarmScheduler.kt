package com.consistencygridwallpaper.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.consistencygridwallpaper.storage.UserPrefs
import java.util.Calendar
import kotlin.random.Random

object ExactAlarmScheduler {
    private const val TAG = "ExactAlarmScheduler"
    private const val ALARM_REQUEST_CODE = 12000

    /**
     * Schedules the next daily wallpaper alarm.
     *
     * - Reads update hour/minute from UserPrefs (defaults to 00:00)
     * - Adds a random jitter of 0–30 minutes to spread 100K+ users across
     *   a 30-minute window, preventing a simultaneous server spike
     * - Stores the jitter so MidnightReceiver can log the actual fire time
     * - Uses setExactAndAllowWhileIdle so it fires even in Doze mode
     */
    fun scheduleNextMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check permissions on Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms — permission not granted. Skipping.")
                return
            }
        }

        val userPrefs = UserPrefs(context)
        val updateHour   = userPrefs.getUpdateHour()   // default 0
        val updateMinute = userPrefs.getUpdateMinute() // default 0

        // 🎲 Jitter: spread users over 0–30 minutes to avoid 100K simultaneous API calls
        val jitterMs = Random.nextLong(0L, 30L * 60L * 1000L) // 0 to 1_800_000 ms

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            // Start from the configured time today
            set(Calendar.HOUR_OF_DAY, updateHour)
            set(Calendar.MINUTE, updateMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If that time has already passed today, move to tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val targetTimeMs = calendar.timeInMillis + jitterMs

        // Human-readable log so ADB can confirm the scheduled time
        val firedAt = Calendar.getInstance().apply { timeInMillis = targetTimeMs }
        val firedHH = firedAt.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val firedMM = firedAt.get(Calendar.MINUTE).toString().padStart(2, '0')
        val jitterMin = (jitterMs / 1000 / 60).toInt()
        Log.d(TAG, "Scheduling alarm for ${firedHH}:${firedMM} (jitter=${jitterMin}min, base=${updateHour}:${updateMinute.toString().padStart(2,'0')})")

        val intent = Intent(context, MidnightReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Wake up the device and allow execution even in Doze mode
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTimeMs,
                pendingIntent
            )
            Log.d(TAG, "✅ Exact alarm scheduled successfully for ${firedHH}:${firedMM}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Failed to schedule exact alarm", e)
        }
    }

    /**
     * Called when SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED fires.
     * If the user just granted the permission, re-schedule the alarm.
     */
    fun rescheduleAfterPermissionGranted(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                Log.d(TAG, "Exact alarm permission (re-)granted — rescheduling alarm")
                val userPrefs = UserPrefs(context)
                if (userPrefs.isAutoUpdateEnabled()) {
                    scheduleNextMidnightAlarm(context)
                }
            }
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
