package com.vetacil.app.network

object OverpassQueryBuilder {

    fun buildVeterinaryQuery(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 5000
    ): String {
        return """
            [out:json][timeout:25];
            node(around:$radiusMeters,$latitude,$longitude)["amenity"="veterinary"];
            out;
        """.trimIndent()
    }
}