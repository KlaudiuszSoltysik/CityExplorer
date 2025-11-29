package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.HexagonApiService
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto

class HexagonRepository(private val apiService: HexagonApiService) {
    suspend fun getCountriesWithCities(): List<GetCountriesWithCitiesDto> {
        return apiService.getCountriesWithCities()
    }

    suspend fun getHexagonsFromCity(city: String, mode: String): GetCityHexagonsDataDto {
        return apiService.getHexagonsFromCity(city, mode)
    }
}