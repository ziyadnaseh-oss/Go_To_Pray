package com.example.go_to_pray

import com.example.go_to_pray.calculator.PrayerTimesCalculator
import com.example.go_to_pray.model.DailySchedule
import com.example.go_to_pray.model.Prayer
import com.example.go_to_pray.model.PrayerTimeItem
import com.example.go_to_pray.repository.City
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PrayerAppUnitTest {

    @Test
    fun testPrayerTimesCalculator_Cairo() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 21)
        }

        // Cairo: 30.0444 N, 31.2357 E, UTC+2
        val times = PrayerTimesCalculator.calculateTimes(
            calendar = calendar,
            latitude = 30.0444,
            longitude = 31.2357,
            timeZoneOffsetHours = 2.0
        )

        assertNotNull(times["Fajr"])
        assertNotNull(times["Sunrise"])
        assertNotNull(times["Dhuhr"])
        assertNotNull(times["Asr"])
        assertNotNull(times["Maghrib"])
        assertNotNull(times["Isha"])

        assertTrue("Fajr time format should be HH:mm", times["Fajr"]!!.matches(Regex("\\d{2}:\\d{2}")))
        assertTrue("Dhuhr time format should be HH:mm", times["Dhuhr"]!!.matches(Regex("\\d{2}:\\d{2}")))
        assertTrue("Maghrib time format should be HH:mm", times["Maghrib"]!!.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun testPrayerTimesCalculator_Makkah() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 21)
        }

        // Makkah: 21.4225 N, 39.8262 E, UTC+3
        val times = PrayerTimesCalculator.calculateTimes(
            calendar = calendar,
            latitude = 21.4225,
            longitude = 39.8262,
            timeZoneOffsetHours = 3.0
        )

        assertNotNull(times["Fajr"])
        assertNotNull(times["Dhuhr"])
        assertNotNull(times["Maghrib"])
    }

    @Test
    fun testCityList_ValidCoordinates() {
        assertFalse(City.CITIES.isEmpty())
        assertTrue(City.CITIES.any { it.nameArabic == "القاهرة" })
        assertTrue(City.CITIES.any { it.nameArabic == "مكة المكرمة" })

        for (city in City.CITIES) {
            assertTrue("Latitude out of range for ${city.nameArabic}", city.latitude in -90.0..90.0)
            assertTrue("Longitude out of range for ${city.nameArabic}", city.longitude in -180.0..180.0)
        }
    }

    @Test
    fun testPrayerEnums_Properties() {
        assertEquals(5, Prayer.values().filter { it.isAdhanPrayer }.size)
        assertFalse(Prayer.SUNRISE.isAdhanPrayer)
        assertTrue(Prayer.FAJR.isAdhanPrayer)
        assertTrue(Prayer.DHUHR.isAdhanPrayer)
        assertTrue(Prayer.ASR.isAdhanPrayer)
        assertTrue(Prayer.MAGHRIB.isAdhanPrayer)
        assertTrue(Prayer.ISHA.isAdhanPrayer)
    }

    @Test
    fun testDailySchedule_GetNextPrayer() {
        val now = 1_000_000L

        val prayers = listOf(
            PrayerTimeItem(Prayer.FAJR, "05:00", "05:00 ص", now - 500_000L),
            PrayerTimeItem(Prayer.DHUHR, "12:00", "12:00 م", now + 200_000L),
            PrayerTimeItem(Prayer.ASR, "15:00", "03:00 م", now + 500_000L)
        )

        val schedule = DailySchedule("2026-11-21", "1448", prayers)
        val next = schedule.getNextPrayer(now)

        assertNotNull(next)
        assertEquals(Prayer.DHUHR, next?.prayer)
    }
}
