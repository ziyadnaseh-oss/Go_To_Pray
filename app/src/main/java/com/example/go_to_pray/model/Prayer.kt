package com.example.go_to_pray.model

enum class Prayer(
    val englishName: String,
    val arabicName: String,
    val isAdhanPrayer: Boolean,
    val alarmId: Int
) {
    FAJR("Fajr", "الفجر", true, 101),
    SUNRISE("Sunrise", "الشروق", false, 0),
    DHUHR("Dhuhr", "الظهر", true, 102),
    ASR("Asr", "العصر", true, 103),
    MAGHRIB("Maghrib", "المغرب", true, 104),
    ISHA("Isha", "العشاء", true, 105);

    companion object {
        fun fromKey(key: String): Prayer {
            return values().firstOrNull { it.englishName.equals(key, ignoreCase = true) } ?: DHUHR
        }
    }
}
