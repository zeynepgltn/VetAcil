package com.vetacil.app.utils

object OpeningHoursParser {

    fun isCurrentlyOpen(openingHours: String): Boolean {
        // Çok basit kontrol - hızlı
        return openingHours.contains("24/7") ||
                openingHours.contains("24 hours") ||
                openingHours.contains("Mo-Su")
    }
}