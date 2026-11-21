package com.example.go_to_pray.calculator

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/**
 * Pure Kotlin astronomical prayer times calculator.
 * Provides 100% offline calculation using the standard Egyptian General Authority
 * of Survey convention (Fajr: 19.5°, Isha: 17.5°).
 */
object PrayerTimesCalculator {

    data class CalculationParams(
        val fajrAngle: Double = 19.5,
        val ishaAngle: Double = 17.5,
        val asrFactor: Double = 1.0 // Shafi'i / Maliki / Hanbali
    )

    fun calculateTimes(
        calendar: Calendar,
        latitude: Double,
        longitude: Double,
        timeZoneOffsetHours: Double = (calendar.timeZone.getOffset(calendar.timeInMillis) / 3600000.0),
        params: CalculationParams = CalculationParams()
    ): Map<String, String> {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // Julian Day relative to J2000.0
        val d = julianDate(year, month, day) - 2451545.0

        // Mean solar anomaly and longitude in degrees
        val m = fixAngle(357.529 + 0.98560028 * d)
        val l = fixAngle(280.459 + 0.98564736 * d)

        // Ecliptic longitude
        val lambda = fixAngle(l + 1.915 * sinDeg(m) + 0.020 * sinDeg(2 * m))

        // Obliquity of ecliptic
        val epsilon = 23.439 - 0.00000036 * d

        // Solar declination in degrees
        val sinDelta = sinDeg(epsilon) * sinDeg(lambda)
        val delta = asinDeg(sinDelta)

        // Equation of Time in hours
        val alpha = atan2Deg(cosDeg(epsilon) * sinDeg(lambda), cosDeg(lambda)) / 15.0
        val eqTime = (l / 15.0) - fixHour(alpha)

        // Solar noon (Dhuhr)
        val noon = fixHour(12.0 + timeZoneOffsetHours - (longitude / 15.0) - eqTime)

        // Sunrise & Sunset (center of sun -0.8333 degrees below horizon)
        val sunriseAngle = -0.8333
        val sunHourAngle = hourAngle(sunriseAngle, latitude, delta)
        val sunrise = if (sunHourAngle.isNaN()) noon - 6.0 else noon - (sunHourAngle / 15.0)
        val sunset = if (sunHourAngle.isNaN()) noon + 6.0 else noon + (sunHourAngle / 15.0)

        // Fajr
        val fajrHourAngle = hourAngle(-params.fajrAngle, latitude, delta)
        val fajr = if (fajrHourAngle.isNaN()) noon - 7.5 else noon - (fajrHourAngle / 15.0)

        // Isha
        val ishaHourAngle = hourAngle(-params.ishaAngle, latitude, delta)
        val isha = if (ishaHourAngle.isNaN()) noon + 7.5 else noon + (ishaHourAngle / 15.0)

        // Asr
        val asrAngle = -acotDeg(params.asrFactor + tanDeg(abs(latitude - delta)))
        val asrHourAngle = hourAngle(asrAngle, latitude, delta)
        val asr = if (asrHourAngle.isNaN()) noon + 3.0 else noon + (asrHourAngle / 15.0)

        return mapOf(
            "Fajr" to formatHours(fajr),
            "Sunrise" to formatHours(sunrise),
            "Dhuhr" to formatHours(noon),
            "Asr" to formatHours(asr),
            "Maghrib" to formatHours(sunset),
            "Isha" to formatHours(isha)
        )
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun hourAngle(altitude: Double, lat: Double, delta: Double): Double {
        val cosH = (sinDeg(altitude) - sinDeg(lat) * sinDeg(delta)) / (cosDeg(lat) * cosDeg(delta))
        if (cosH > 1.0 || cosH < -1.0) return Double.NaN
        return acosDeg(cosH)
    }

    private fun sinDeg(deg: Double): Double = sin(Math.toRadians(deg))
    private fun cosDeg(deg: Double): Double = cos(Math.toRadians(deg))
    private fun tanDeg(deg: Double): Double = tan(Math.toRadians(deg))
    private fun asinDeg(x: Double): Double = Math.toDegrees(asin(x.coerceIn(-1.0, 1.0)))
    private fun acosDeg(x: Double): Double = Math.toDegrees(acos(x.coerceIn(-1.0, 1.0)))
    private fun atan2Deg(y: Double, x: Double): Double = Math.toDegrees(atan2(y, x))
    private fun acotDeg(x: Double): Double = Math.toDegrees(atan(1.0 / x))

    private fun fixAngle(deg: Double): Double {
        val a = deg % 360.0
        return if (a < 0) a + 360.0 else a
    }

    private fun fixHour(hour: Double): Double {
        val h = hour % 24.0
        return if (h < 0) h + 24.0 else h
    }

    private fun formatHours(hours: Double): String {
        val totalMinutes = (fixHour(hours) * 60.0).roundToInt()
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return String.format(java.util.Locale.US, "%02d:%02d", h, m)
    }
}
