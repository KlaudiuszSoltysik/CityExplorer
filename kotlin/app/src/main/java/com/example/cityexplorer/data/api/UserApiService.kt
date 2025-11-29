package com.example.cityexplorer.data.api

import com.example.cityexplorer.data.dtos.LoginRequestDto
import com.example.cityexplorer.data.dtos.LoginResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface UserApiService {
    @POST("/user/validate-login-token")
    suspend fun validateLoginToken(
        @Body request: LoginRequestDto
    ): LoginResponseDto
}