package com.example.go_to_pray.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.go_to_pray.model.Prayer
import com.example.go_to_pray.repository.PrayerRepository
import java.util.Calendar

object PrayerAlarmScheduler {

    private const val TAG = "PrayerAlarmScheduler"
    const val ACTION_PRAYER_ALARM = "com.example.go_to_pray.ACTION_PRAYER_ALARM"
    const val ACTION_MIDNIGHT_REFRESH = "com.example.go_to_pray.ACTION_MIDNIGHT_REFRESH"

    const val EXTRA_PRAYER_KEY = "extra_prayer_key"
    const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    const val EXTRA_PRAYER_TIME = "extra_prayer_time"

    const val REQUEST_CODE_MIDNIGHT = 100
    const val REQUEST_CODE_TEST = 999

    /**
     * Schedules alarms for all remaining prayers today (and tomorrow's Fajr if needed),
     * plus the midnight refresh.
     */
    fun scheduleNextAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val repository = PrayerRepository.getInstance(context)

        val nowCal = Calendar.getInstance()
        val nowMillis = nowCal.timeInMillis
        val todaySchedule = repository.getTodaySchedule(nowCal)

        var alarmsScheduledCount = 0

        for (item in todaySchedule.prayers) {
            if (!item.prayer.isAdhanPrayer) continue

            if (item.triggerTimeMillis > nowMillis) {
                scheduleExactAlarm(
                    context = context,
                    alarmManager = alarmManager,
                    triggerAtMillis = item.triggerTimeMillis,
                    requestCode = item.prayer.alarmId,
                    prayerKey = item.prayer.englishName,
                    prayerName = item.prayer.arabicName,
                    prayerTime = item.timeFormatted12
                )
                alarmsScheduledCount++
            }
        }

        // If all prayers for today have passed, schedule tomorrow's Fajr
        if (alarmsScheduledCount == 0) {
            val tomorrowCal = (nowCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val tomorrowSchedule = repository.getScheduleForCalendar(tomorrowCal)
            val fajr = tomorrowSchedule.prayers.firstOrNull { it.prayer == Prayer.FAJR }
            if (fajr != null) {
                scheduleExactAlarm(
                    context = context,
                    alarmManager = alarmManager,
                    triggerAtMillis = fajr.triggerTimeMillis,
                    requestCode = Prayer.FAJR.alarmId,
                    prayerKey = Prayer.FAJR.englishName,
                    prayerName = Prayer.FAJR.arabicName,
                    prayerTime = fajr.timeFormatted12
                )
            }
        }

        // Schedule midnight refresh alarm
        scheduleMidnightRefresh(context, alarmManager)
    }

    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        requestCode: Int,
        prayerKey: String,
        prayerName: String,
        prayerTime: String
    ) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER_KEY, prayerKey)
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            putExtra(EXTRA_PRAYER_TIME, prayerTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled alarm for $prayerName at $triggerAtMillis")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission error", e)
        }
    }

    private fun scheduleMidnightRefresh(context: Context, alarmManager: AlarmManager) {
        val midnightCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_MIDNIGHT_REFRESH
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_MIDNIGHT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                midnightCal.timeInMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Midnight alarm scheduling error", e)
        }
    }

    /**
     * For manual testing: triggers AdhanAlarm in [delaySeconds] seconds.
     */
    fun scheduleTestAlarm(context: Context, delaySeconds: Long = 3) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000)

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER_KEY, Prayer.DHUHR.englishName)
            putExtra(EXTRA_PRAYER_NAME, Prayer.DHUHR.arabicName)
            putExtra(EXTRA_PRAYER_TIME, "12:00 م")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
