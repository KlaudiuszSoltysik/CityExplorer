package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.UserApiClient
import com.example.cityexplorer.data.dtos.GetUserStatisticsResponseDto
import com.example.cityexplorer.data.dtos.ValidateAuthorizationTokenResponseDto
import com.example.cityexplorer.data.dtos.ValidateLoginTokenRequestDto
import com.example.cityexplorer.data.dtos.ValidateLoginTokenResponseDto
import com.example.cityexplorer.data.util.TokenService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val apiService: UserApiClient,
    private val tokenService: TokenService
) {
    suspend fun validateLoginToken(googleToken: String): Result<ValidateLoginTokenResponseDto> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val requestDto = ValidateLoginTokenRequestDto(googleToken = googleToken)

                val response = apiService.validateLoginToken(requestDto)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Login failed: ${response.code()} ${response.message()}."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getLoggedUser(): Result<ValidateAuthorizationTokenResponseDto> =
        withContext(Dispatchers.IO) {
            val token = tokenService.getToken()
                ?: return@withContext Result.failure(Exception("No local token found."))

            return@withContext try {
                val response = apiService.getLoggedUser("Bearer $token")

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Fetch user failed: ${response.code()}."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteUserAccount(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val token = tokenService.getToken()
                ?: return@withContext Result.failure(Exception("No local token found."))

            return@withContext try {
                val response = apiService.deleteUserAccount("Bearer $token")

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Delete account failed: ${response.code()}."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getUserStatistics(city: String): Result<GetUserStatisticsResponseDto> =
        withContext(Dispatchers.IO) {
            val token = tokenService.getToken()
                ?: return@withContext Result.failure(Exception("No local token found."))

            return@withContext try {
                val response = apiService.getUserStatistics("Bearer $token", city)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Get stats failed: ${response.code()}."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}