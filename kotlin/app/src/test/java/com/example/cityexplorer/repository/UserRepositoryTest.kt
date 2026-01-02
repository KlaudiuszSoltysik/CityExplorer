package com.example.cityexplorer.repository

import com.example.cityexplorer.data.api.UserApiClient
import com.example.cityexplorer.data.dtos.GetUserStatisticsResponseDto
import com.example.cityexplorer.data.dtos.ValidateAuthorizationTokenResponseDto
import com.example.cityexplorer.data.dtos.ValidateLoginTokenResponseDto
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.TokenService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import io.mockk.every
import io.mockk.verify

class UserRepositoryTest {
    private val apiService = mockk<UserApiClient>()
    private val tokenService = mockk<TokenService>(relaxed = true)

    private val repository = UserRepository(apiService, tokenService)

    @Test
    fun `validateLoginToken returns success when API call is successful`() = runTest {
        val googleToken = "valid_google_token"
        val expectedResponse = ValidateLoginTokenResponseDto(true, "jwt_token")

        coEvery {
            apiService.validateLoginToken(match { it.googleToken == googleToken })
        } returns Response.success(expectedResponse)

        val result = repository.validateLoginToken(googleToken)

        assertTrue(result.isSuccess)
        assertEquals(expectedResponse, result.getOrThrow())

        coVerify(exactly = 1) { apiService.validateLoginToken(any()) }
    }

    @Test
    fun `validateLoginToken returns failure when API returns error`() = runTest {
        val googleToken = "invalid_google_token"
        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        coEvery {
            apiService.validateLoginToken(any())
        } returns Response.error(401, errorBody)

        val result = repository.validateLoginToken(googleToken)

        assertTrue(result.isFailure)

        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("Login failed: 401"))
    }

    @Test
    fun `validateLoginToken returns failure when API call throws exception`() = runTest {
        val googleToken = "token"

        coEvery {
            apiService.validateLoginToken(any())
        } throws RuntimeException("Network error")

        val result = repository.validateLoginToken(googleToken)

        assertTrue(result.isFailure)

        val exception = result.exceptionOrNull()
        assertEquals("Network error", exception?.message)
    }

    @Test
    fun `getLoggedUser returns success when token exists and API call is successful`() = runTest {
        val token = "valid_token"
        val expectedUser = ValidateAuthorizationTokenResponseDto("user_123")

        every { tokenService.getToken() } returns token

        coEvery {
            apiService.getLoggedUser("Bearer $token")
        } returns Response.success(expectedUser)

        val result = repository.getLoggedUser()

        assertTrue(result.isSuccess)
        assertEquals(expectedUser, result.getOrThrow())

        coVerify(exactly = 1) { apiService.getLoggedUser("Bearer $token") }
    }

    @Test
    fun `getLoggedUser returns failure immediately when local token is missing`() = runTest {
        every { tokenService.getToken() } returns null

        val result = repository.getLoggedUser()

        assertTrue(result.isFailure)
        assertEquals("No local token found.", result.exceptionOrNull()?.message)

        coVerify(exactly = 0) { apiService.getLoggedUser(any()) }
    }

    @Test
    fun `getLoggedUser returns failure when API returns error`() = runTest {
        val token = "expired_token"
        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        every { tokenService.getToken() } returns token

        coEvery {
            apiService.getLoggedUser(any())
        } returns Response.error(404, errorBody)

        val result = repository.getLoggedUser()

        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message
        assertNotNull(msg)
        assertTrue(msg!!.contains("Fetch user failed: 404"))
    }

    @Test
    fun `getLoggedUser returns failure when exception is thrown`() = runTest {
        val token = "token"
        every { tokenService.getToken() } returns token

        coEvery { apiService.getLoggedUser(any()) } throws RuntimeException("Connection error")

        val result = repository.getLoggedUser()

        assertTrue(result.isFailure)
        assertEquals("Connection error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `deleteUserAccount calls API, clears token and returns success`() = runTest {
        val token = "valid_token"

        every { tokenService.getToken() } returns token

        coEvery {
            apiService.deleteUserAccount("Bearer $token")
        } returns Response.success(Unit)

        val result = repository.deleteUserAccount()

        assertTrue(result.isSuccess)

        verify(exactly = 1) { tokenService.clearToken() }

        coVerify(exactly = 1) { apiService.deleteUserAccount(any()) }
    }

    @Test
    fun `deleteUserAccount returns failure immediately when local token is missing`() = runTest {
        every { tokenService.getToken() } returns null

        val result = repository.deleteUserAccount()

        assertTrue(result.isFailure)
        assertEquals("No local token found.", result.exceptionOrNull()?.message)

        coVerify(exactly = 0) { apiService.deleteUserAccount(any()) }
        verify(exactly = 0) { tokenService.clearToken() }
    }

    @Test
    fun `deleteUserAccount returns failure and DOES NOT clear token when API fails`() = runTest {
        val token = "valid_token"
        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        every { tokenService.getToken() } returns token

        coEvery {
            apiService.deleteUserAccount(any())
        } returns Response.error(500, errorBody)

        val result = repository.deleteUserAccount()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Delete account failed: 500"))

        verify(exactly = 0) { tokenService.clearToken() }
    }

    @Test
    fun `deleteUserAccount returns failure when exception is thrown`() = runTest {
        val token = "token"
        every { tokenService.getToken() } returns token

        coEvery { apiService.deleteUserAccount(any()) } throws RuntimeException("Network error")

        val result = repository.deleteUserAccount()

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)

        verify(exactly = 0) { tokenService.clearToken() }
    }

    @Test
    fun `getUserStatistics returns success with data when API call is successful`() = runTest {
        val city = "Poznań"
        val token = "valid_token"

        val expectedStats = GetUserStatisticsResponseDto(
            explored = 12.5,
            progress = 50,
            hexagonCount = 100,
            playTime = 3600,
            distance = 5000,
            ranking = 5,
            userCount = 200
        )

        every { tokenService.getToken() } returns token

        coEvery {
            apiService.getUserStatistics("Bearer $token", city)
        } returns Response.success(expectedStats)

        val result = repository.getUserStatistics(city)

        assertTrue(result.isSuccess)
        assertEquals(expectedStats, result.getOrThrow())

        coVerify(exactly = 1) { apiService.getUserStatistics("Bearer $token", city) }
    }

    @Test
    fun `getUserStatistics returns failure immediately when local token is missing`() = runTest {
        val city = "Poznań"
        every { tokenService.getToken() } returns null

        val result = repository.getUserStatistics(city)

        assertTrue(result.isFailure)
        assertEquals("No local token found.", result.exceptionOrNull()?.message)

        coVerify(exactly = 0) { apiService.getUserStatistics(any(), any()) }
    }

    @Test
    fun `getUserStatistics returns failure when API returns error`() = runTest {
        val city = "Poznań"
        val token = "valid_token"
        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        every { tokenService.getToken() } returns token

        coEvery {
            apiService.getUserStatistics(any(), any())
        } returns Response.error(404, errorBody)

        val result = repository.getUserStatistics(city)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Get stats failed: 404"))
    }

    @Test
    fun `getUserStatistics returns failure when exception is thrown`() = runTest {
        val city = "Poznań"
        val token = "token"
        every { tokenService.getToken() } returns token

        coEvery { apiService.getUserStatistics(any(), any()) } throws RuntimeException("Timeout")

        val result = repository.getUserStatistics(city)

        assertTrue(result.isFailure)
        assertEquals("Timeout", result.exceptionOrNull()?.message)
    }
}