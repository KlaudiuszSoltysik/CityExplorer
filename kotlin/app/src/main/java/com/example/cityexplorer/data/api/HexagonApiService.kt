package com.example.cityexplorer.data.api

import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import retrofit2.http.GET
import retrofit2.http.Query

interface HexagonApiService {
    @GET("/hexagon/get-countries-with-cities")
    suspend fun getCountriesWithCities(): List<GetCountriesWithCitiesDto>

    @GET("/hexagon/get-hexagons-from-city")
    suspend fun getHexagonsFromCity(
        @Query("city") city: String,
        @Query("mode") mode: String
    ): GetCityHexagonsDataDto
}