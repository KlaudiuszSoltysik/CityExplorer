package com.example.cityexplorer.data.api

import com.example.cityexplorer.data.dtos.GetCurrentVersionResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface VersionApiClient {
    @GET("/version/get-current-version")
    suspend fun getCurrentVersion(
        @Query("key") key: String
    ): Response<GetCurrentVersionResponseDto>
}