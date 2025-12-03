package com.vetacil.app.repository

import android.location.Location
import com.vetacil.app.model.VeterinaryClinic
import com.vetacil.app.network.OverpassApiService
import com.vetacil.app.network.OverpassQueryBuilder
import com.vetacil.app.utils.OpeningHoursParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VeterinaryRepository(private val apiService: OverpassApiService) {

    // Cache
    private var cachedClinics: List<VeterinaryClinic>? = null
    private var lastSearchLocation: Pair<Double, Double>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION = 5 * 60 * 1000 // 5 dakika

    suspend fun searchNearbyVeterinaries(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 5000,
        onlyOpen: Boolean = false
    ): Result<List<VeterinaryClinic>> = withContext(Dispatchers.IO) {

        val currentLocation = latitude to longitude
        val currentTime = System.currentTimeMillis()

        // Cache kontrolü - aynı konum ve cache süresi geçmemişse
        if (cachedClinics != null &&
            lastSearchLocation == currentLocation &&
            (currentTime - cacheTimestamp) < CACHE_DURATION) {

            val filtered = cachedClinics!!.filter { !onlyOpen || it.isOpen }
            return@withContext Result.success(filtered)
        }

        try {
            val query = OverpassQueryBuilder.buildVeterinaryQuery(latitude, longitude, radiusMeters)
            val response = apiService.searchVeterinaries(query)

            if (response.isSuccessful) {
                val clinics = response.body()?.elements?.mapNotNull { element ->
                    element.tags?.let { tags ->
                        val address = buildAddress(tags.street, tags.houseNumber, tags.city)
                        val isOpen = tags.openingHours?.let {
                            OpeningHoursParser.isCurrentlyOpen(it)
                        } ?: false

                        val clinicLocation = Location("").apply {
                            this.latitude = element.lat
                            this.longitude = element.lon
                        }
                        val userLocation = Location("").apply {
                            this.latitude = latitude
                            this.longitude = longitude
                        }
                        val distance = userLocation.distanceTo(clinicLocation) / 1000

                        VeterinaryClinic(
                            id = element.id.toString(),
                            name = tags.name ?: "İsimsiz Veteriner",
                            latitude = element.lat,
                            longitude = element.lon,
                            address = address,
                            phone = tags.phone?.replace(" ", ""),
                            openingHours = tags.openingHours,
                            isOpen = isOpen,
                            distance = distance
                        )
                    }
                }?.sortedBy { it.distance } ?: emptyList()

                // Cache'e kaydet
                cachedClinics = clinics
                lastSearchLocation = currentLocation
                cacheTimestamp = currentTime

                val filtered = clinics.filter { !onlyOpen || it.isOpen }
                Result.success(filtered)
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            // HATA DURUMUNDA TEST VERİSİ DÖNDÜR (SADECE GELİŞTİRME)
            val testClinics = listOf(
                VeterinaryClinic(
                    id = "1",
                    name = "Test Veteriner Kliniği 1",
                    latitude = latitude + 0.01,
                    longitude = longitude + 0.01,
                    address = "Test Adres 1",
                    phone = "05551234567",
                    openingHours = "Mo-Fr 09:00-18:00",
                    isOpen = true,
                    distance = 1.2f
                ),
                VeterinaryClinic(
                    id = "2",
                    name = "Test Veteriner Kliniği 2",
                    latitude = latitude - 0.01,
                    longitude = longitude - 0.01,
                    address = "Test Adres 2",
                    phone = "05559876543",
                    openingHours = "24/7",
                    isOpen = true,
                    distance = 2.5f
                )
            )
            Result.success(testClinics)
        }
    }

    private fun buildAddress(street: String?, houseNumber: String?, city: String?): String {
        return listOfNotNull(street, houseNumber, city).joinToString(", ")
    }
}