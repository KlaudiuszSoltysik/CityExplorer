package com.example.cityexplorer.data.api

import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import retrofit2.http.GET

interface HexagonApiService {
    @GET("/hexagon/get-countries-with-cities")
    suspend fun getCountriesWithCities(): List<GetCountriesWithCitiesDto>
}