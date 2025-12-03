package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.UserApiClient
import com.example.cityexplorer.data.dtos.GetUserResponseDto
import com.example.cityexplorer.data.dtos.LoginResponseDto

class UserRepository(private val apiService: UserApiClient) {
    suspend fun validateLoginToken(token: String): LoginResponseDto {
        return apiService.validateLoginToken(token)
    }

    suspend fun getLoggedUser(token: String): GetUserResponseDto {
        return apiService.getLoggedUser(token)
    }
}