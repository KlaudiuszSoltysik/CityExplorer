package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.UserApiService
import com.example.cityexplorer.data.dtos.LoginRequestDto
import com.example.cityexplorer.data.dtos.LoginResponseDto

class UserRepository(private val apiService: UserApiService) {
    suspend fun validateLoginToken(requestDto: LoginRequestDto): LoginResponseDto {
        return apiService.validateLoginToken(requestDto)
    }
}