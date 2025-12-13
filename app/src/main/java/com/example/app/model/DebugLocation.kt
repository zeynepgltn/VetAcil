package com.vetacil.app.model

import org.osmdroid.util.GeoPoint

/**
 * Debug ve test amaçlı kullanılacak konum modeli
 */
data class DebugLocation(
    val name: String,
    val geoPoint: GeoPoint,
    val description: String = ""
)

/**
 * Önceden tanımlı test konumları
 */
object DebugLocations {
    val ISTANBUL_TAKSIM = DebugLocation(
        name = "İstanbul - Taksim",
        geoPoint = GeoPoint(41.0369, 28.9850),
        description = "Taksim Meydanı"
    )

    val ISTANBUL_KADIKOY = DebugLocation(
        name = "İstanbul - Kadıköy",
        geoPoint = GeoPoint(40.9904, 29.0242),
        description = "Kadıköy Merkez"
    )

    val ANKARA_KIZILAY = DebugLocation(
        name = "Ankara - Kızılay",
        geoPoint = GeoPoint(39.9199, 32.8543),
        description = "Kızılay Meydanı"
    )

    val IZMIR_KONAK = DebugLocation(
        name = "İzmir - Konak",
        geoPoint = GeoPoint(38.4189, 27.1287),
        description = "Konak Meydanı"
    )

    val ANTALYA_KALEICI = DebugLocation(
        name = "Antalya - Kaleiçi",
        geoPoint = GeoPoint(36.8841, 30.7056),
        description = "Kaleiçi Bölgesi"
    )

    val BURSA_HEYKEL = DebugLocation(
        name = "Bursa - Heykel",
        geoPoint = GeoPoint(40.1885, 29.0610),
        description = "Heykel Meydanı"
    )

    val ESKISEHIR_MERKEZ = DebugLocation(
        name = "Eskişehir - Merkez",
        geoPoint = GeoPoint(39.7767, 30.5206),
        description = "Kent Merkezi"
    )

    val MANISA_TURGUTLU = DebugLocation(
        name = "Manisa - Turgutlu",
        geoPoint = GeoPoint(38.5022, 27.7010),
        description = "Turgutlu Merkez"
    )

    /**
     * Tüm test konumlarının listesi
     */
    fun getAllLocations(): List<DebugLocation> {
        return listOf(
            ISTANBUL_TAKSIM,
            ISTANBUL_KADIKOY,
            ANKARA_KIZILAY,
            IZMIR_KONAK,
            ANTALYA_KALEICI,
            BURSA_HEYKEL,
            ESKISEHIR_MERKEZ,
            MANISA_TURGUTLU
        )
    }
}