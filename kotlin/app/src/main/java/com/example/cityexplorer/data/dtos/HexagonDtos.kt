package com.example.cityexplorer.data.dtos

import android.location.Location
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class GetCountriesWithCitiesDto(
    val country: String,
    val cities: List<String>
)

@Serializable
data class GetCityHexagonsDataDto(
    val bbox: List<Double>,
    val hexagons: List<HexagonsDto>
)

@Serializable
data class HexagonsDto(
    val id: String,
    val boundaries: List<List<Double>>,
    val center: List<Double>,
    val weight: Double,
    val progress: Double = 0.0
)

@Serializable
data class GetPoisFromHexagonDto(
    val name: String,
    val type: String,
    val isPromoted: Boolean
)

@Serializable
data class SelectedHexagonDto(
    val weight: Double = 0.0,
    val pois: List<GetPoisFromHexagonDto> = emptyList()
)

@Serializable
data class PostLocationBatchDto(
    val token: String,
    val locations: List<LocationDto>
)

@Serializable
data class LocationDto(
    val lat: Double,
    val lon: Double,
    val timestamp: String
)

@Serializable
data class SyncResponseDto(
    val updatedHexagons: List<HexagonUpdateDto>
)

@Serializable
data class HexagonUpdateDto(
    val hexagonId: String,
    val progress: Double
)

private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun Location.toDto(): LocationDto {
    return LocationDto(
        lat = this.latitude,
        lon = this.longitude,
        timestamp = iso8601Format.format(Date(this.time))
    )
}