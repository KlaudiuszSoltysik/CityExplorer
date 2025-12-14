package com.example.cityexplorer.data.api

import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.dtos.GetPoisFromHexagonDto
import com.example.cityexplorer.data.dtos.HexagonProgressDto
import com.example.cityexplorer.data.dtos.PostLocationBatchDto
import com.example.cityexplorer.data.dtos.SyncResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface HexagonApiClient {
    @GET("/hexagon/get-countries-with-cities")
    suspend fun getCountriesWithCities(): List<GetCountriesWithCitiesDto>

    @GET("/hexagon/get-hexagons-from-city")
    suspend fun getHexagonsFromCity(
        @Query("city") city: String
    ): GetCityHexagonsDataDto

    @GET("/hexagon/get-pois-from-hexagon")
    suspend fun getPoisFromHexagon(
        @Query("hexagonId") hexagonId: String
    ): List<GetPoisFromHexagonDto>

    @POST("/hexagon/post-location-batch")
    suspend fun postLocationBatch(
        @Header("Authorization") token: String,
        @Body locationDtos: PostLocationBatchDto
    ): Response<SyncResponseDto>

    @GET("/hexagon/get-hexagon-progresses")
    suspend fun getHexagonProgresses(
        @Header("Authorization") token: String,
        @Query("city") city: String
    ): List<HexagonProgressDto>
}