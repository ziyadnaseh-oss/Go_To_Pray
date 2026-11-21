package com.example.go_to_pray.model

data class PrayerTimeItem(
    val prayer: Prayer,
    val timeFormatted24: String, // e.g. "05:12"
    val timeFormatted12: String, // e.g. "05:12 ص"
    val triggerTimeMillis: Long,
    val isNext: Boolean = false
)

data class DailySchedule(
    val gregorianDate: String,
    val hijriDate: String,
    val prayers: List<PrayerTimeItem>
) {
    fun getNextPrayer(nowMillis: Long = System.currentTimeMillis()): PrayerTimeItem? {
        return prayers
            .filter { it.prayer.isAdhanPrayer && it.triggerTimeMillis > nowMillis }
            .minByOrNull { it.triggerTimeMillis }
    }
}
