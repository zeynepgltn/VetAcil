package com.vetacil.app.model

data class VeterinaryClinic(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val phone: String? = null,
    val openingHours: String? = null,
    val isOpen: Boolean = false,
    val distance: Float? = null // Kullanıcıya olan mesafe (km)
)