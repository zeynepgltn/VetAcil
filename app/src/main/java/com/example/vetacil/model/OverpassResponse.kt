package com.vetacil.app.model

import com.google.gson.annotations.SerializedName

data class OverpassResponse(
    val elements: List<OverpassElement>
)

data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: OverpassTags?
)

data class OverpassTags(

    val name: String?,
    @SerializedName("addr:street") val street: String?,
    @SerializedName("addr:housenumber") val houseNumber: String?,
    @SerializedName("addr:city") val city: String?,
    val phone: String?,
    @SerializedName("opening_hours") val openingHours: String?,
    val amenity: String?
)