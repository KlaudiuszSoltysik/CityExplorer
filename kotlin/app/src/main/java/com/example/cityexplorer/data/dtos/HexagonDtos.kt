package com.example.cityexplorer.data.dtos

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class GetCountriesWithCitiesDto(
    val country: String,
    val cities: List<String>
)

@Serializable
@Keep
data class GetCityHexagonsDataDto(
    val bbox: List<Double>,
    val hexagons: List<HexagonsDto>
)

@Serializable
@Keep
data class HexagonsDto(
    val id: String,
    val boundaries: List<List<Double>>,
    val center: List<Double>,
    val weight: Double,
    val progress: Double = 0.0
)

@Serializable
@Keep
data class GetPoisFromHexagonDto(
    val name: String,
    val type: String,
    val isPromoted: Boolean
)

@Serializable
@Keep
data class SelectedHexagonDto(
    val weight: Double = 0.0,
    val pois: List<GetPoisFromHexagonDto> = emptyList()
)

@Serializable
@Keep
data class LocationDto(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)

@Serializable
@Keep
data class PostLocationBatchDto(
    val locations: List<LocationDto>
)


@Serializable
@Keep
data class SyncResponseDto(
    val updatedHexagons: List<HexagonProgressDto>,
    val token: String?
)

@Serializable
@Keep
data class HexagonProgressDto(
    val hexagonId: String,
    val progress: Double
)

@Serializable
@Keep
data class SimpleLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
