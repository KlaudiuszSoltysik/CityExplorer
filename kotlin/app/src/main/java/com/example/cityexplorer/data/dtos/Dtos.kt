package com.example.cityexplorer.data.dtos

import kotlinx.serialization.Serializable

@Serializable
data class GetCountriesWithCitiesDto(
    val country: String,
    val cities: List<String>
)

@Serializable
data class GetHexagonsFromCityDto(
    val id: String,
    val boundaries: List<List<Double>>,
    val weight: Double
)
