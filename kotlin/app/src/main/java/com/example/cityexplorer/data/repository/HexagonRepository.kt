package com.example.cityexplorer.data.repository

import com.example.cityexplorer.data.api.HexagonApiService
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto

class HexagonRepository(private val apiService: HexagonApiService) {
    suspend fun getCountriesWithCities(): List<GetCountriesWithCitiesDto> {
        return apiService.getCountriesWithCities()
    }
}