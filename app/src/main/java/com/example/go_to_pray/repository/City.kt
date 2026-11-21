package com.example.go_to_pray.repository

data class City(
    val nameArabic: String,
    val nameEnglish: String,
    val countryArabic: String,
    val latitude: Double,
    val longitude: Double
) {
    override fun toString(): String = "$nameArabic - $countryArabic"

    companion object {
        val DEFAULT_CITY = City("القاهرة", "Cairo", "مصر", 30.0444, 31.2357)

        val CITIES = listOf(
            DEFAULT_CITY,
            City("الإسكندرية", "Alexandria", "مصر", 31.2001, 29.9187),
            City("الجيزة", "Giza", "مصر", 30.0131, 31.2089),
            City("المنصورة", "Mansoura", "مصر", 31.0409, 31.3785),
            City("طنطا", "Tanta", "مصر", 30.7865, 31.0004),
            City("بورسعيد", "Port Said", "مصر", 31.2653, 32.3019),
            City("السويس", "Suez", "مصر", 29.9668, 32.5498),
            City("الإسماعيلية", "Ismailia", "مصر", 30.5965, 32.2715),
            City("أسيوط", "Asyut", "مصر", 27.1783, 31.1859),
            City("سوهاج", "Sohag", "مصر", 26.5590, 31.6957),
            City("الأقصر", "Luxor", "مصر", 25.6872, 32.6396),
            City("أسوان", "Aswan", "مصر", 24.0889, 32.8998),
            City("مكة المكرمة", "Makkah", "السعودية", 21.4225, 39.8262),
            City("المدينة المنورة", "Madinah", "السعودية", 24.5247, 39.5692),
            City("الرياض", "Riyadh", "السعودية", 24.7136, 46.6753),
            City("جدة", "Jeddah", "السعودية", 21.5433, 39.1728),
            City("القدس الشريف", "Jerusalem", "فلسطين", 31.7683, 35.2137),
            City("عَمّان", "Amman", "الأردن", 31.9454, 35.9284),
            City("دمشق", "Damascus", "سوريا", 33.5138, 36.2765),
            City("بيروت", "Beirut", "لبنان", 33.8938, 35.5018),
            City("بغداد", "Baghdad", "العراق", 33.3152, 44.3661),
            City("الكويت", "Kuwait City", "الكويت", 29.3759, 47.9774),
            City("الدوحة", "Doha", "قطر", 25.2854, 51.5310),
            City("أبوظبي", "Abu Dhabi", "الإمارات", 24.4539, 54.3773),
            City("دبي", "Dubai", "الإمارات", 25.2048, 55.2708),
            City("مسقط", "Muscat", "عُمان", 23.5880, 58.3829),
            City("المنامة", "Manama", "البحرين", 26.2285, 50.5860),
            City("طرابلس", "Tripoli", "ليبيا", 32.8872, 13.1913),
            City("تونس", "Tunis", "تونس", 36.8065, 10.1815),
            City("الجزائر", "Algiers", "الجزائر", 36.7538, 3.0588),
            City("الرباط", "Rabat", "المغرب", 34.0209, -6.8416),
            City("الخرطوم", "Khartoum", "السودان", 15.5007, 32.5599)
        )
    }
}
