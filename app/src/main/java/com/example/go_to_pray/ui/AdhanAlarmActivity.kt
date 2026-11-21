package com.example.go_to_pray.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.go_to_pray.R
import com.example.go_to_pray.alarm.PrayerAlarmReceiver

class AdhanAlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRAYER_KEY = "extra_prayer_key"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_TIME = "extra_prayer_time"
        private const val TAG = "AdhanAlarmActivity"
        private const val AUTO_STOP_DELAY_MS = 210_000L // 3.5 minutes max
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isStopped = false

    private val autoStopRunnable = Runnable {
        stopAdhanAndFinish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupLockScreenFlags()
        setContentView(R.layout.activity_adhan_alarm)

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "الظهر"
        val prayerTime = intent.getStringExtra(EXTRA_PRAYER_TIME) ?: ""

        val tvPrayerName = findViewById<TextView>(R.id.tvAlarmPrayerName)
        val tvPrayerTime = findViewById<TextView>(R.id.tvAlarmPrayerTime)
        val btnStopAdhan = findViewById<View>(R.id.btnStopAdhan)

        tvPrayerName.text = "أذان $prayerName"
        tvPrayerTime.text = prayerTime

        btnStopAdhan.setOnClickListener {
            stopAdhanAndFinish()
        }

        startAdhanPlayback()
        startVibration()

        // Auto stop after 3.5 minutes if untouched to prevent indefinite battery drain
        handler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startAdhanPlayback() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        requestAudioFocus()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        var player: MediaPlayer? = null

        // 1. Try playing bundled Saudi/Makkah Adhan MP3 from res/raw
        try {
            player = MediaPlayer.create(this, R.raw.saudi_adhan, audioAttributes, audioManager?.generateAudioSessionId() ?: 0)
            if (player == null) {
                // Secondary create attempt
                player = MediaPlayer.create(this, R.raw.saudi_adhan)
                player?.setAudioAttributes(audioAttributes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing raw adhan audio, will fallback to system alarm ringtone", e)
            player = null
        }

        // 2. Safe Audio Fallback: system alarm or ringtone
        if (player == null) {
            try {
                val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                player = MediaPlayer().apply {
                    setDataSource(applicationContext, alarmUri)
                    setAudioAttributes(audioAttributes)
                    prepare()
                }
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback ringtone also failed", fallbackEx)
            }
        }

        mediaPlayer = player

        mediaPlayer?.let { mp ->
            try {
                mp.isLooping = false
                mp.setOnCompletionListener {
                    stopAdhanAndFinish()
                }
                mp.start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start media player", e)
            }
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { /* handle if needed */ }
                .build()

            audioFocusRequest?.let { am.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // repeat
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    private fun stopAdhanAndFinish() {
        if (isStopped) return
        isStopped = true

        handler.removeCallbacks(autoStopRunnable)

        // 1. Stop audio playback immediately
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player", e)
        }

        // 2. Stop vibration
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibrator", e)
        }

        // 3. Release audio focus
        try {
            val am = audioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio focus", e)
        }

        // 4. Cancel notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(PrayerAlarmReceiver.NOTIFICATION_ID)

        // 5. Close activity
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdhanAndFinish()
    }
}
