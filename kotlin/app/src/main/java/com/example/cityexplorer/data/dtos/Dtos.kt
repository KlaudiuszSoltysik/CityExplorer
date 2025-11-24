package com.example.cityexplorer.data.dtos

import kotlinx.serialization.Serializable

@Serializable
data class GetCountriesWithCitiesDto(
    val country: String,
    val cities: List<String>
)