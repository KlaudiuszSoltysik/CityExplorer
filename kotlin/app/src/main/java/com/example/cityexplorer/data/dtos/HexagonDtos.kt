package com.example.cityexplorer.data.dtos

import kotlinx.serialization.Serializable

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
data class LocationDto(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)

@Serializable
data class PostLocationBatchDto(
    val locations: List<LocationDto>
)


@Serializable
data class SyncResponseDto(
    val updatedHexagons: List<HexagonProgressDto>,
    val token: String?
)

@Serializable
data class HexagonProgressDto(
    val hexagonId: String,
    val progress: Double
)

@Serializable
data class SimpleLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
