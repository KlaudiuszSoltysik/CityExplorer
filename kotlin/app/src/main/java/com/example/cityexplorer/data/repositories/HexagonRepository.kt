package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.HexagonApiService
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.dtos.GetPoisFromHexagonDto

class HexagonRepository(private val apiService: HexagonApiService) {
    suspend fun getCountriesWithCities(): List<GetCountriesWithCitiesDto> {
        return apiService.getCountriesWithCities()
    }

    suspend fun getHexagonsFromCity(city: String): GetCityHexagonsDataDto {
        return apiService.getHexagonsFromCity(city)
    }

    suspend fun getPoisFromHexagon(hexagonId: String): List<GetPoisFromHexagonDto> {
        return apiService.getPoisFromHexagon(hexagonId)
    }
}