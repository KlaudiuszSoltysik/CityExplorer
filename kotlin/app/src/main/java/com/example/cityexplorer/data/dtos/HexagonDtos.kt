package com.example.cityexplorer.data.dtos

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetCountriesWithCitiesResponseDto(
    @SerialName("country")
    val country: String,

    @SerialName("cities")
    val cities: List<String>
)

@Serializable
data class GetHexagonsFromCityResponseDto(
    @SerialName("bbox")
    val bbox: List<Double>,

    @SerialName("hexagons")
    val hexagons: List<Hexagon> = emptyList()
)

@Serializable
data class GetPoisFromHexagonResponseDto(
    @SerialName("name")
    val name: String,

    @SerialName("type")
    val type: String,

    @SerialName("isPromoted")
    val isPromoted: Boolean
)

@Serializable
data class PostLocationBatchRequestDto(
    @SerialName("locations")
    val locations: List<CustomLocationStringTimestamp>
)

@Serializable
data class CustomLocationStringTimestamp(
    @SerialName("latitude")
    val latitude: Double,

    @SerialName("longitude")
    val longitude: Double,

    @SerialName("timestamp")
    val timestamp: String
)

@Serializable
data class CustomLocationDoubleTimestamp(
    @SerialName("latitude")
    val latitude: Double,

    @SerialName("longitude")
    val longitude: Double,

    @SerialName("timestamp")
    val timestamp: Long
)

@Serializable
data class PostLocationBatchResponseDto(
    @SerialName("updatedHexagons")
    val updatedHexagons: List<HexagonProgress>?,

    @SerialName("token")
    val token: String?
)

@Serializable
data class HexagonProgress(
    @SerialName("hexagonId")
    val hexagonId: String = "",

    @SerialName("progress")
    val progress: Double
)

@Serializable
data class Hexagon(
    @SerialName("id")
    val id: String,

    @SerialName("boundaries")
    val boundaries: List<List<Double>>,

    @SerialName("center")
    val center: List<Double>,

    @SerialName("weight")
    val weight: Double,

    @SerialName("progress")
    val progress: Double = 0.0
)

@Serializable
data class SelectedHexagon(
    @SerialName("weight")
    val weight: Double = 0.0,

    @SerialName("pois")
    val pois: List<GetPoisFromHexagonResponseDto>? = emptyList()
)


@Serializable
data class GenerateRouteRequestDto(
    @SerialName("userLatitude")
    val userLatitude: Double,

    @SerialName("userLongitude")
    val userLongitude: Double,

    @SerialName("duration")
    val duration: Int
)

@Serializable
data class GenerateRouteResponseDto(
    @SerialName("jobId")
    val jobId: String,

    @SerialName("token")
    val token: String?
)

@Keep
@Serializable
data class WorkerResult(
    @SerialName("route")
    val route: List<String> = emptyList()
)
