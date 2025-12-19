package com.example.cityexplorer.data.api

import com.example.cityexplorer.data.dtos.GetUserResponseDto
import com.example.cityexplorer.data.dtos.GetUserStatisticsDto
import com.example.cityexplorer.data.dtos.LoginResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface UserApiClient {
    @POST("/user/validate-login-token")
    suspend fun validateLoginToken(
        @Body request: String
    ): LoginResponseDto

    @POST("/user/get-logged-user")
    suspend fun getLoggedUser(
        @Header("Authorization") token: String
    ): GetUserResponseDto

    @POST("/user/delete-user-account")
    suspend fun deleteUserAccount(
        @Header("Authorization") token: String
    ): Response<Unit>

    @GET("/user/get-user-statistics")
    suspend fun getUserStatistics(
        @Header("Authorization") token: String,
        @Query("city") city: String
    ): GetUserStatisticsDto
}