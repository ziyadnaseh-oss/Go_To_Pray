package com.example.go_to_pray.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.go_to_pray.calculator.PrayerTimesCalculator
import com.example.go_to_pray.model.DailySchedule
import com.example.go_to_pray.model.Prayer
import com.example.go_to_pray.model.PrayerTimeItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class PrayerRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val PREFS_NAME = "prayer_times_prefs"
        private const val KEY_CITY_NAME = "key_city_name"
        private const val KEY_COUNTRY_NAME = "key_country_name"
        private const val KEY_LATITUDE = "key_latitude"
        private const val KEY_LONGITUDE = "key_longitude"
        private const val KEY_CACHE_PREFIX = "cached_month_"
        private const val KEY_LAST_FETCH = "last_fetch_timestamp"

        @Volatile
        private var instance: PrayerRepository? = null

        fun getInstance(context: Context): PrayerRepository {
            return instance ?: synchronized(this) {
                instance ?: PrayerRepository(context).also { instance = it }
            }
        }
    }

    // --- Location Management ---

    fun getSavedCity(): City {
        val name = prefs.getString(KEY_CITY_NAME, City.DEFAULT_CITY.nameArabic) ?: City.DEFAULT_CITY.nameArabic
        val country = prefs.getString(KEY_COUNTRY_NAME, City.DEFAULT_CITY.countryArabic) ?: City.DEFAULT_CITY.countryArabic
        val lat = prefs.getFloat(KEY_LATITUDE, City.DEFAULT_CITY.latitude.toFloat()).toDouble()
        val lng = prefs.getFloat(KEY_LONGITUDE, City.DEFAULT_CITY.longitude.toFloat()).toDouble()
        return City(name, name, country, lat, lng)
    }

    fun saveCity(city: City) {
        prefs.edit()
            .putString(KEY_CITY_NAME, city.nameArabic)
            .putString(KEY_COUNTRY_NAME, city.countryArabic)
            .putFloat(KEY_LATITUDE, city.latitude.toFloat())
            .putFloat(KEY_LONGITUDE, city.longitude.toFloat())
            .apply()
    }

    fun saveCustomCoordinates(latitude: Double, longitude: Double, label: String = "موقعي الحالي") {
        prefs.edit()
            .putString(KEY_CITY_NAME, label)
            .putString(KEY_COUNTRY_NAME, "")
            .putFloat(KEY_LATITUDE, latitude.toFloat())
            .putFloat(KEY_LONGITUDE, longitude.toFloat())
            .apply()
    }

    // --- Schedule Retrieval & Offline Calculation ---

    fun getTodaySchedule(calendar: Calendar = Calendar.getInstance()): DailySchedule {
        return getScheduleForCalendar(calendar)
    }

    fun getScheduleForCalendar(calendar: Calendar): DailySchedule {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val cachedJson = getCachedMonthJson(year, month)
        if (cachedJson != null) {
            val parsed = parseScheduleFromJson(cachedJson, calendar)
            if (parsed != null) {
                return parsed
            }
        }

        // Offline fallback calculation
        return calculateScheduleOffline(calendar)
    }

    private fun parseScheduleFromJson(jsonString: String, calendar: Calendar): DailySchedule? {
        return try {
            val root = JSONObject(jsonString)
            val dataArray = root.optJSONArray("data") ?: return null
            val dayIndex = calendar.get(Calendar.DAY_OF_MONTH) - 1
            if (dayIndex < 0 || dayIndex >= dataArray.length()) return null

            val dayObj = dataArray.getJSONObject(dayIndex)
            val timings = dayObj.getJSONObject("timings")
            val dateObj = dayObj.optJSONObject("date")

            val gregorianDate = dateObj?.optJSONObject("gregorian")?.optString("date")
                ?: formatDateGregorian(calendar)

            val hijriObj = dateObj?.optJSONObject("hijri")
            val hijriDate = if (hijriObj != null) {
                val hDay = hijriObj.optString("day")
                val hMonth = hijriObj.optJSONObject("month")?.optString("ar") ?: ""
                val hYear = hijriObj.optString("year")
                "$hDay $hMonth $hYear هـ"
            } else {
                formatDateGregorian(calendar)
            }

            val prayersList = mutableListOf<PrayerTimeItem>()
            val nowMillis = System.currentTimeMillis()

            for (prayer in Prayer.values()) {
                val rawTime = timings.optString(prayer.englishName, "")
                val cleanTime = cleanTimeString(rawTime)
                if (cleanTime.isNotEmpty()) {
                    val triggerMillis = computeMillis(calendar, cleanTime)
                    val time12 = formatTo12Hours(cleanTime)
                    prayersList.add(
                        PrayerTimeItem(
                            prayer = prayer,
                            timeFormatted24 = cleanTime,
                            timeFormatted12 = time12,
                            triggerTimeMillis = triggerMillis,
                            isNext = false
                        )
                    )
                }
            }

            // Flag next prayer
            val nextPrayer = prayersList
                .filter { it.prayer.isAdhanPrayer && it.triggerTimeMillis > nowMillis }
                .minByOrNull { it.triggerTimeMillis }

            val updatedList = prayersList.map { item ->
                item.copy(isNext = (item.prayer == nextPrayer?.prayer))
            }

            DailySchedule(gregorianDate, hijriDate, updatedList)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateScheduleOffline(calendar: Calendar): DailySchedule {
        val city = getSavedCity()
        val timesMap = PrayerTimesCalculator.calculateTimes(
            calendar = calendar,
            latitude = city.latitude,
            longitude = city.longitude
        )

        val prayersList = mutableListOf<PrayerTimeItem>()
        val nowMillis = System.currentTimeMillis()

        for (prayer in Prayer.values()) {
            val cleanTime = timesMap[prayer.englishName] ?: "12:00"
            val triggerMillis = computeMillis(calendar, cleanTime)
            val time12 = formatTo12Hours(cleanTime)
            prayersList.add(
                PrayerTimeItem(
                    prayer = prayer,
                    timeFormatted24 = cleanTime,
                    timeFormatted12 = time12,
                    triggerTimeMillis = triggerMillis,
                    isNext = false
                )
            )
        }

        val nextPrayer = prayersList
            .filter { it.prayer.isAdhanPrayer && it.triggerTimeMillis > nowMillis }
            .minByOrNull { it.triggerTimeMillis }

        val updatedList = prayersList.map { item ->
            item.copy(isNext = (item.prayer == nextPrayer?.prayer))
        }

        val gregorianStr = formatDateGregorian(calendar)
        return DailySchedule(gregorianStr, "حساب محلي دقيق", updatedList)
    }

    // --- Networking (HttpURLConnection) ---

    fun refreshScheduleMonthAsync(calendar: Calendar = Calendar.getInstance(), onFinished: (Boolean) -> Unit) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val city = getSavedCity()

        executor.execute {
            var success = false
            var conn: HttpURLConnection? = null
            try {
                // Method 5 = Egyptian General Authority of Survey
                val urlString = "https://api.aladhan.com/v1/calendar/$year/$month?latitude=${city.latitude}&longitude=${city.longitude}&method=5"
                val url = URL(urlString)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "GoToPray/1.0 (Android Native)")
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()

                    val json = sb.toString()
                    val jsonObj = JSONObject(json)
                    if (jsonObj.optInt("code", 0) == 200) {
                        saveMonthJson(year, month, json)
                        prefs.edit().putLong(KEY_LAST_FETCH, System.currentTimeMillis()).apply()
                        success = true
                    }
                }
            } catch (e: Exception) {
                success = false
            } finally {
                conn?.disconnect()
            }

            onFinished(success)
        }
    }

    private fun getCachedMonthJson(year: Int, month: Int): String? {
        val key = "$KEY_CACHE_PREFIX${year}_${month}"
        return prefs.getString(key, null)
    }

    private fun saveMonthJson(year: Int, month: Int, json: String) {
        val key = "$KEY_CACHE_PREFIX${year}_${month}"
        prefs.edit().putString(key, json).apply()
    }

    // --- Time String Helpers ---

    private fun cleanTimeString(raw: String): String {
        return raw.split(" ")[0].trim()
    }

    private fun computeMillis(cal: Calendar, time24: String): Long {
        val parts = time24.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val target = cal.clone() as Calendar
        target.set(Calendar.HOUR_OF_DAY, h)
        target.set(Calendar.MINUTE, m)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        return target.timeInMillis
    }

    private fun formatTo12Hours(time24: String): String {
        val parts = time24.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val amPm = if (h < 12) "ص" else "م"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format(Locale.getDefault(), "%02d:%02d %s", h12, m, amPm)
    }

    private fun formatDateGregorian(calendar: Calendar): String {
        val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", Locale.forLanguageTag("ar"))
        return sdf.format(calendar.time)
    }
}
