package com.example.cityexplorer.data.api

import com.example.cityexplorer.data.dtos.GenerateRouteRequestDto
import com.example.cityexplorer.data.dtos.GenerateRouteResponseDto
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesResponseDto
import com.example.cityexplorer.data.dtos.GetHexagonsFromCityResponseDto
import com.example.cityexplorer.data.dtos.GetPoisFromHexagonResponseDto
import com.example.cityexplorer.data.dtos.HexagonProgress
import com.example.cityexplorer.data.dtos.PostLocationBatchRequestDto
import com.example.cityexplorer.data.dtos.PostLocationBatchResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface HexagonApiClient {
    @GET("/hexagon/get-countries-with-cities")
    suspend fun getCountriesWithCities(): Response<List<GetCountriesWithCitiesResponseDto>>

    @GET("/hexagon/get-hexagons-from-city")
    suspend fun getHexagonsFromCity(
        @Query("city") city: String
    ): Response<GetHexagonsFromCityResponseDto>

    @GET("/hexagon/get-pois-from-hexagon")
    suspend fun getPoisFromHexagon(
        @Query("hexagonId") hexagonId: String
    ): Response<List<GetPoisFromHexagonResponseDto>>

    @POST("/hexagon/post-location-batch")
    suspend fun postLocationBatch(
        @Header("Authorization") token: String,
        @Body requestDto: PostLocationBatchRequestDto
    ): Response<PostLocationBatchResponseDto>

    @GET("/hexagon/get-hexagon-progresses")
    suspend fun getHexagonProgresses(
        @Header("Authorization") token: String,
        @Query("city") city: String
    ): Response<List<HexagonProgress>>

    @POST("/hexagon/generate-route")
    suspend fun generateRoute(
        @Header("Authorization") token: String,
        @Body requestDto: GenerateRouteRequestDto
    ): Response<GenerateRouteResponseDto>
}