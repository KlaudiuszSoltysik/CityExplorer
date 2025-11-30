package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.UserApiService
import com.example.cityexplorer.data.dtos.GetUserRequestDto
import com.example.cityexplorer.data.dtos.GetUserResponseDto
import com.example.cityexplorer.data.dtos.LoginRequestDto
import com.example.cityexplorer.data.dtos.LoginResponseDto

class UserRepository(private val apiService: UserApiService) {
    suspend fun validateLoginToken(loginRequestDto: LoginRequestDto): LoginResponseDto {
        return apiService.validateLoginToken(loginRequestDto)
    }

    suspend fun getLoggedUser(getUserRequestDto: GetUserRequestDto): GetUserResponseDto {
        return apiService.getLoggedUser(getUserRequestDto)
    }
}