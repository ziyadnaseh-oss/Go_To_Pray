package com.example.go_to_pray.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.go_to_pray.R
import com.example.go_to_pray.repository.PrayerRepository
import com.example.go_to_pray.ui.AdhanAlarmActivity

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "adhan_alarm_channel_id"
        const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        when (action) {
            PrayerAlarmScheduler.ACTION_MIDNIGHT_REFRESH -> {
                PrayerRepository.getInstance(context).refreshScheduleMonthAsync {
                    PrayerAlarmScheduler.scheduleNextAlarms(context)
                }
            }

            PrayerAlarmScheduler.ACTION_PRAYER_ALARM -> {
                val prayerKey = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_KEY) ?: "DHUHR"
                val prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME) ?: "الظهر"
                val prayerTime = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME) ?: ""

                // 1. Acquire temporary wake lock to ensure CPU keeps running while launching
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "GoToPray:PrayerAlarmWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(60_000L) // 60 seconds safety timeout
                }

                // 2. Create notification channel if needed
                createNotificationChannel(context)

                // 3. Prepare Full-Screen Intent for AdhanAlarmActivity
                val fullScreenIntent = Intent(context, AdhanAlarmActivity::class.java).apply {
                    putExtra(AdhanAlarmActivity.EXTRA_PRAYER_KEY, prayerKey)
                    putExtra(AdhanAlarmActivity.EXTRA_PRAYER_NAME, prayerName)
                    putExtra(AdhanAlarmActivity.EXTRA_PRAYER_TIME, prayerTime)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    prayerKey.hashCode(),
                    fullScreenIntent,
                    pendingFlags
                )

                // 4. Build high-priority alarm notification
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mosque)
                    .setContentTitle("حان الآن موعد أذان $prayerName")
                    .setContentText("اضغط لإيقاف الأذان")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setContentIntent(fullScreenPendingIntent)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .build()

                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, notification)

                // 5. Attempt direct launch of AdhanAlarmActivity
                try {
                    context.startActivity(fullScreenIntent)
                } catch (e: Exception) {
                    // Handled by fullScreenIntent in notification
                }

                // 6. Schedule subsequent alarms
                PrayerAlarmScheduler.scheduleNextAlarms(context)
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    setBypassDnd(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
