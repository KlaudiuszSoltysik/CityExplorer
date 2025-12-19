package com.example.cityexplorer.data.repositories

import android.util.Log
import com.example.cityexplorer.data.api.UserApiClient
import com.example.cityexplorer.data.dtos.GetUserResponseDto
import com.example.cityexplorer.data.dtos.GetUserStatisticsDto
import com.example.cityexplorer.data.dtos.LoginResponseDto
import com.example.cityexplorer.data.util.TokenService

class UserRepository(
    private val apiService: UserApiClient,
    private val tokenService: TokenService
) {
    suspend fun validateLoginToken(token: String): LoginResponseDto {
        return apiService.validateLoginToken(token)
    }

    suspend fun getLoggedUser(): Result<GetUserResponseDto> {
        val token = tokenService.getToken() ?: return Result.failure(Exception("No token"))
        return try {
            val response = apiService.getLoggedUser("Bearer $token")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUserAccount(): Result<Unit> {
        val token = tokenService.getToken() ?: return Result.failure(Exception("No token"))
        return try {
            apiService.deleteUserAccount("Bearer $token")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserStatistics(city: String): Result<GetUserStatisticsDto> {
        val token = tokenService.getToken() ?: return Result.failure(Exception("No token"))
        return try {
            val response = apiService.getUserStatistics("Bearer $token", city)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}