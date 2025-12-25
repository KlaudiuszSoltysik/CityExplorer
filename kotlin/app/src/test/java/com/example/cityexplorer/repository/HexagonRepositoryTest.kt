package com.example.cityexplorer.repository

import com.example.cityexplorer.data.api.HexagonApiClient
import com.example.cityexplorer.data.api.VersionApiClient
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.dtos.HexagonProgressDto
import com.example.cityexplorer.data.dtos.PostLocationBatchDto
import com.example.cityexplorer.data.dtos.SyncResponseDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.repositories.InvalidTokenException
import com.example.cityexplorer.data.util.CacheService
import com.example.cityexplorer.data.util.TokenService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

class HexagonRepositoryTest {
    private val hexagonApiClient = mockk<HexagonApiClient>()
    private val versionApiClient = mockk<VersionApiClient>()
    private val cacheService = mockk<CacheService>(relaxed = true)
    private val tokenService = mockk<TokenService>(relaxed = true)

    private val repository = HexagonRepository(hexagonApiClient, versionApiClient, cacheService, tokenService)

    @Test
    fun `getCountriesWithCities returns cached data when versions match`() = runBlocking {
        val key = "get-countries-with-cities"
        val version = "1"
        val cachedList = listOf(GetCountriesWithCitiesDto("Poland", listOf("Poznań")))

        coEvery { versionApiClient.getCurrentVersion(key).string() } returns version
        coEvery { cacheService.getCachedVersion(key) } returns version

        coEvery { cacheService.getCachedData<List<GetCountriesWithCitiesDto>>(key, any()) } returns cachedList

        val result = repository.getCountriesWithCities(forceRefresh = false)

        assertEquals(cachedList, result)
        coVerify(exactly = 0) { hexagonApiClient.getCountriesWithCities() }
    }

    @Test
    fun `getCountriesWithCities returns fallback data when network fails`() = runBlocking {
        val key = "get-countries-with-cities"
        val cachedList = listOf(GetCountriesWithCitiesDto("Poland", listOf("Poznań")))

        coEvery { versionApiClient.getCurrentVersion(key) } throws Exception("Network Error")

        coEvery { cacheService.getCachedData<List<GetCountriesWithCitiesDto>>(key, any()) } returns cachedList

        val result = repository.getCountriesWithCities(forceRefresh = false)

        assertEquals(cachedList, result)

        coVerify { versionApiClient.getCurrentVersion(key) }
    }

    @Test
    fun `getHexagonsFromCity returns Success when network data is fetched`() = runBlocking {
        val city = "Poznań"
        val key = "get-hexagons-from-city-$city"
        val remoteVersion = "1"
        val mockData = GetCityHexagonsDataDto(bbox = listOf(1.0, 2.0, 3.0, 4.0), hexagons = emptyList())

        coEvery { versionApiClient.getCurrentVersion(key).string() } returns remoteVersion
        coEvery { cacheService.getCachedVersion(key) } returns "v1"

        coEvery { hexagonApiClient.getHexagonsFromCity(city) } returns mockData

        val result = repository.getHexagonsFromCity(city)

        assert(result is HexagonRepository.GetHexagonsFromCityResult.Success)
        assertEquals(mockData, (result as HexagonRepository.GetHexagonsFromCityResult.Success).data)

        coVerify { cacheService.saveToCache(key, remoteVersion, mockData) }
    }

    @Test
    fun `getHexagonsFromCity returns Fallback when network fails but cache exists`() = runBlocking {
        val city = "Poznań"
        val key = "get-hexagons-from-city-$city"
        val cachedData = GetCityHexagonsDataDto(bbox = listOf(1.0, 2.0), hexagons = emptyList())

        coEvery { versionApiClient.getCurrentVersion(key) } throws Exception("No internet")

        coEvery { cacheService.getCachedData<GetCityHexagonsDataDto>(key, any()) } returns cachedData

        val result = repository.getHexagonsFromCity(city)

        assert(result is HexagonRepository.GetHexagonsFromCityResult.Fallback)
        assertEquals(cachedData, (result as HexagonRepository.GetHexagonsFromCityResult.Fallback).data)
    }

    @Test
    fun `postLocationBatch returns true and emits updates on success`() = runBlocking {
        val mockToken = "old_token"
        val newToken = "new_token"
        val locations = PostLocationBatchDto(emptyList())
        val updates = listOf(HexagonProgressDto("hex_1", 0.5))
        val mockResponse = SyncResponseDto(updatedHexagons = updates, token = newToken)

        coEvery { tokenService.getToken() } returns mockToken
        coEvery { hexagonApiClient.postLocationBatch("Bearer $mockToken", locations) } returns Response.success(mockResponse)
        coEvery { tokenService.saveToken(newToken) } returns Unit

        val result = repository.postLocationBatch(locations)

        assert(result)

        coVerify { tokenService.saveToken(newToken) }

        coVerify { hexagonApiClient.postLocationBatch("Bearer $mockToken", locations) }
    }

    @Test
    fun `postLocationBatch throws InvalidTokenException when token is null`() = runBlocking {
        coEvery { tokenService.getToken() } returns null

        try {
            repository.postLocationBatch(PostLocationBatchDto(emptyList()))
            assert(false) { "Exception!" }
        } catch (e: Exception) {
            assert(e is InvalidTokenException)
        }
    }

    @Test
    fun `getHexagonProgresses clears token and throws InvalidTokenException on 401 error`() = runBlocking {
        val city = "Poznan"
        val mockToken = "expired_token"

        val errorResponseBody = "{\"error\":\"Unauthorized\"}".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(Response.error<Any>(401, errorResponseBody))

        coEvery { tokenService.getToken() } returns mockToken
        coEvery { hexagonApiClient.getHexagonProgresses("Bearer $mockToken", city) } throws httpException
        coEvery { tokenService.clearToken() } returns Unit

        try {
            repository.getHexagonProgresses(city)
            assert(false) { "Exception!" }
        } catch (e: Exception) {
            assert(e is InvalidTokenException)
        }

        coVerify { tokenService.clearToken() }
    }

    @Test
    fun `getHexagonProgresses returns list when token is valid`() = runBlocking {
        val city = "Poznań"
        val mockToken = "valid_token"
        val expectedProgress = listOf(HexagonProgressDto("hex_1", 1.0))

        coEvery { tokenService.getToken() } returns mockToken
        coEvery { hexagonApiClient.getHexagonProgresses("Bearer $mockToken", city) } returns expectedProgress

        val result = repository.getHexagonProgresses(city)

        assertEquals(expectedProgress, result)
        coVerify(exactly = 0) { tokenService.clearToken() }
    }
}