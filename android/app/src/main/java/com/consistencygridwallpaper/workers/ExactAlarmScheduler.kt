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

        val userPrefs = UserPrefs(context)
        val updateHour   = userPrefs.getUpdateHour()   // default 0
        val updateMinute = userPrefs.getUpdateMinute() // default 0

        // Small jitter 0..5 minutes
        val jitterMs = Random.nextLong(0L, 5L * 60L * 1000L) // 0 to 300_000 ms (5 minutes)

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, updateHour)
            set(Calendar.MINUTE, updateMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If that time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val targetTimeMs = calendar.timeInMillis + jitterMs

        val firedAt = Calendar.getInstance().apply { timeInMillis = targetTimeMs }
        val firedHH = firedAt.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val firedMM = firedAt.get(Calendar.MINUTE).toString().padStart(2, '0')
        val jitterSec = (jitterMs / 1000).toInt()
        Log.d(TAG, "⏰ Alarm scheduled for ${firedHH}:${firedMM} (jitter=${jitterSec}s, base=${updateHour}:${updateMinute.toString().padStart(2,'0')})")

        val intent = Intent(context, MidnightReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTimeMs,
                    pendingIntent
                )
                Log.d(TAG, "✅ setExactAndAllowWhileIdle registered — Layer 1 (Exact Alarm) active")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTimeMs,
                    pendingIntent
                )
                Log.d(TAG, "✅ setAndAllowWhileIdle registered — Layer 1 (Doze-Bypassing Alarm) active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AlarmManager registration error — attempting setAndAllowWhileIdle fallback", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTimeMs,
                    pendingIntent
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback alarm registration failed", e2)
            }
        }

        // Always schedule Layer 2 PeriodicWork backup as well
        MidnightWorkScheduler.schedule(context)
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
