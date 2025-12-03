package com.example.cityexplorer.data.api

import com.example.cityexplorer.data.dtos.GetUserResponseDto
import com.example.cityexplorer.data.dtos.LoginResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface UserApiClient {
    @POST("/user/validate-login-token")
    suspend fun validateLoginToken(
        @Body request: String
    ): LoginResponseDto

    @POST("/user/get-logged-user")
    suspend fun getLoggedUser(
        @Body request: String
    ): GetUserResponseDto
}