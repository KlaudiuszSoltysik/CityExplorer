package com.example.cityexplorer.data.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface VersionApiService {
    @GET("/version/get-current-version")
    suspend fun getCurrentVersion(
        @Query("key") key: String?
    ): ResponseBody
}