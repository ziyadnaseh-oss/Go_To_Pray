package com.example.go_to_pray

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.go_to_pray.alarm.PrayerAlarmScheduler
import com.example.go_to_pray.model.DailySchedule
import com.example.go_to_pray.model.Prayer
import com.example.go_to_pray.model.PrayerTimeItem
import com.example.go_to_pray.repository.City
import com.example.go_to_pray.repository.PrayerRepository
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var repository: PrayerRepository
    private var currentSchedule: DailySchedule? = null

    // UI elements
    private lateinit var tvCurrentCity: TextView
    private lateinit var tvHijriDate: TextView
    private lateinit var tvGregorianDate: TextView
    private lateinit var tvNextPrayerTitle: TextView
    private lateinit var tvNextPrayerCountdown: TextView
    private lateinit var tvNextPrayerTimeExact: TextView
    private lateinit var btnDetectLocation: ImageButton
    private lateinit var btnRefreshTimes: ImageButton
    private lateinit var btnTestAlarm: View

    // Prayer Rows
    private lateinit var rowFajr: View
    private lateinit var rowSunrise: View
    private lateinit var rowDhuhr: View
    private lateinit var rowAsr: View
    private lateinit var rowMaghrib: View
    private lateinit var rowIsha: View

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var isTickerRunning = false

    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            countdownHandler.postDelayed(this, 1000L)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "يرجى تفعيل الإشعارات ليصلك تنبيه الأذان في موعده", Toast.LENGTH_LONG).show()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            detectUserLocation()
        } else {
            Toast.makeText(this, "تم رفض إذن الموقع، يمكنك اختيار مدينتك يدوياً", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = PrayerRepository.getInstance(this)

        initViews()
        setupListeners()

        // 1. Instant load of cached/offline prayer schedule with ZERO delay
        loadAndDisplayTodaySchedule()

        // 2. Schedule exact system alarms for today's prayers
        PrayerAlarmScheduler.scheduleNextAlarms(this)

        // 3. Request permissions if required
        checkRequiredPermissions()

        // 4. Background refresh of monthly schedule
        repository.refreshScheduleMonthAsync { success ->
            if (success) {
                runOnUiThread {
                    loadAndDisplayTodaySchedule()
                }
            }
        }
    }

    private fun initViews() {
        tvCurrentCity = findViewById(R.id.tvCurrentCity)
        tvHijriDate = findViewById(R.id.tvHijriDate)
        tvGregorianDate = findViewById(R.id.tvGregorianDate)
        tvNextPrayerTitle = findViewById(R.id.tvNextPrayerTitle)
        tvNextPrayerCountdown = findViewById(R.id.tvNextPrayerCountdown)
        tvNextPrayerTimeExact = findViewById(R.id.tvNextPrayerTimeExact)
        btnDetectLocation = findViewById(R.id.btnDetectLocation)
        btnRefreshTimes = findViewById(R.id.btnRefreshTimes)
        btnTestAlarm = findViewById(R.id.btnTestAlarm)

        rowFajr = findViewById(R.id.rowFajr)
        rowSunrise = findViewById(R.id.rowSunrise)
        rowDhuhr = findViewById(R.id.rowDhuhr)
        rowAsr = findViewById(R.id.rowAsr)
        rowMaghrib = findViewById(R.id.rowMaghrib)
        rowIsha = findViewById(R.id.rowIsha)
    }

    private fun setupListeners() {
        tvCurrentCity.setOnClickListener {
            showCitySelectionDialog()
        }

        btnDetectLocation.setOnClickListener {
            requestLocationDetection()
        }

        btnRefreshTimes.setOnClickListener {
            Toast.makeText(this, "جاري تحديث مواقيت الشهر...", Toast.LENGTH_SHORT).show()
            repository.refreshScheduleMonthAsync { success ->
                runOnUiThread {
                    loadAndDisplayTodaySchedule()
                    PrayerAlarmScheduler.scheduleNextAlarms(this)
                    if (success) {
                        Toast.makeText(this, "تم تحديث المواقيت بنجاح", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "تعذر التحديث عبر الإنترنت، تم استخدام الحساب المحلي الدقيق", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnTestAlarm.setOnClickListener {
            Toast.makeText(this, "سيتم تشغيل شاشة الأذان بعد ثانيتين للتجربة...", Toast.LENGTH_SHORT).show()
            PrayerAlarmScheduler.scheduleTestAlarm(this, delaySeconds = 2)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isTickerRunning) {
            isTickerRunning = true
            countdownHandler.post(countdownRunnable)
        }
    }

    override fun onStop() {
        super.onStop()
        isTickerRunning = false
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    private fun loadAndDisplayTodaySchedule() {
        val city = repository.getSavedCity()
        tvCurrentCity.text = "${city.nameArabic}، ${city.countryArabic} (اضغط للتغيير)"

        val schedule = repository.getTodaySchedule()
        currentSchedule = schedule

        tvHijriDate.text = schedule.hijriDate
        tvGregorianDate.text = schedule.gregorianDate

        // Populate prayer rows
        for (item in schedule.prayers) {
            when (item.prayer) {
                Prayer.FAJR -> bindPrayerRow(rowFajr, item)
                Prayer.SUNRISE -> bindPrayerRow(rowSunrise, item)
                Prayer.DHUHR -> bindPrayerRow(rowDhuhr, item)
                Prayer.ASR -> bindPrayerRow(rowAsr, item)
                Prayer.MAGHRIB -> bindPrayerRow(rowMaghrib, item)
                Prayer.ISHA -> bindPrayerRow(rowIsha, item)
            }
        }

        updateNextPrayerDisplay()
    }

    private fun bindPrayerRow(view: View, item: PrayerTimeItem) {
        val tvName = view.findViewById<TextView>(R.id.tvRowPrayerName)
        val tvTime = view.findViewById<TextView>(R.id.tvRowPrayerTime)
        val tvBadgeNext = view.findViewById<TextView>(R.id.tvRowBadgeNext)
        val viewActive = view.findViewById<View>(R.id.viewActiveIndicator)

        tvName.text = item.prayer.arabicName
        tvTime.text = item.timeFormatted12

        if (item.isNext) {
            tvBadgeNext.visibility = View.VISIBLE
            viewActive.visibility = View.VISIBLE
            view.setBackgroundResource(R.drawable.bg_card_highlighted)
        } else {
            tvBadgeNext.visibility = View.GONE
            viewActive.visibility = View.GONE
            view.setBackgroundResource(R.drawable.bg_card_rounded)
        }
    }

    private fun updateNextPrayerDisplay() {
        val schedule = currentSchedule ?: return
        val nextPrayer = schedule.getNextPrayer()

        if (nextPrayer != null) {
            tvNextPrayerTitle.text = "الصلاة القادمة: ${nextPrayer.prayer.arabicName}"
            tvNextPrayerTimeExact.text = "موعد الأذان: ${nextPrayer.timeFormatted12}"
        } else {
            tvNextPrayerTitle.text = "انتهت صلوات اليوم"
            tvNextPrayerTimeExact.text = "الصلاة القادمة: فجر الغد"
        }
        updateCountdown()
    }

    private fun updateCountdown() {
        val schedule = currentSchedule ?: return
        val nextPrayer = schedule.getNextPrayer()
        val now = System.currentTimeMillis()

        if (nextPrayer != null) {
            val diff = nextPrayer.triggerTimeMillis - now
            if (diff > 0) {
                val hours = diff / (1000 * 60 * 60)
                val minutes = (diff / (1000 * 60)) % 60
                val seconds = (diff / 1000) % 60
                tvNextPrayerCountdown.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                // Time reached: refresh schedule
                loadAndDisplayTodaySchedule()
            }
        } else {
            // Calculate until tomorrow Fajr
            val tomorrowCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val tomorrowSchedule = repository.getScheduleForCalendar(tomorrowCal)
            val tomorrowFajr = tomorrowSchedule.prayers.firstOrNull { it.prayer == Prayer.FAJR }
            if (tomorrowFajr != null) {
                val diff = tomorrowFajr.triggerTimeMillis - now
                if (diff > 0) {
                    val hours = diff / (1000 * 60 * 60)
                    val minutes = (diff / (1000 * 60)) % 60
                    val seconds = (diff / 1000) % 60
                    tvNextPrayerCountdown.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    loadAndDisplayTodaySchedule()
                }
            } else {
                tvNextPrayerCountdown.text = "--:--:--"
            }
        }
    }

    private fun showCitySelectionDialog() {
        val cities = City.CITIES
        val cityNames = cities.map { "${it.nameArabic} (${it.countryArabic})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.change_city)
            .setItems(cityNames) { _, which ->
                val selectedCity = cities[which]
                repository.saveCity(selectedCity)
                loadAndDisplayTodaySchedule()
                PrayerAlarmScheduler.scheduleNextAlarms(this)

                // Background fetch for newly chosen city
                repository.refreshScheduleMonthAsync { success ->
                    if (success) {
                        runOnUiThread {
                            loadAndDisplayTodaySchedule()
                        }
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun requestLocationDetection() {
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCoarse) {
            detectUserLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    private fun detectUserLocation() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        var bestLocation: Location? = null

        try {
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            }
        } catch (e: SecurityException) {
            // Permission issue handled
        }

        if (bestLocation != null) {
            repository.saveCustomCoordinates(bestLocation.latitude, bestLocation.longitude, "موقعي الحالي")
            loadAndDisplayTodaySchedule()
            PrayerAlarmScheduler.scheduleNextAlarms(this)
            Toast.makeText(this, "تم تحديث الموقع بنجاح", Toast.LENGTH_SHORT).show()

            repository.refreshScheduleMonthAsync { success ->
                if (success) {
                    runOnUiThread {
                        loadAndDisplayTodaySchedule()
                    }
                }
            }
        } else {
            Toast.makeText(this, "تعذر التقاط الموقع بدقة، تم الاحتفاظ بالمدينة الحالية", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkRequiredPermissions() {
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Exact alarm check for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            if (alarmManager?.canScheduleExactAlarms() == false) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Ignored if intent cannot be started
                }
            }
        }
    }
}
