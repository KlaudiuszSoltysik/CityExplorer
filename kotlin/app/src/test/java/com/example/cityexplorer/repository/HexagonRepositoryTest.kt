package com.example.cityexplorer.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.cityexplorer.data.api.HexagonApiClient
import com.example.cityexplorer.data.api.VersionApiClient
import com.example.cityexplorer.data.dtos.GenerateRouteResponseDto
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesResponseDto
import com.example.cityexplorer.data.dtos.GetCurrentVersionResponseDto
import com.example.cityexplorer.data.dtos.GetHexagonsFromCityResponseDto
import com.example.cityexplorer.data.dtos.GetPoisFromHexagonResponseDto
import com.example.cityexplorer.data.dtos.Hexagon
import com.example.cityexplorer.data.dtos.HexagonProgress
import com.example.cityexplorer.data.dtos.PostLocationBatchRequestDto
import com.example.cityexplorer.data.dtos.PostLocationBatchResponseDto
import com.example.cityexplorer.data.dtos.WorkerResult
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.repositories.InvalidTokenException
import com.example.cityexplorer.data.util.CacheService
import com.example.cityexplorer.data.util.TokenService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.Response
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertThrows
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue


class HexagonRepositoryTest {
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true) {
        every { edit() } returns editor
    }

    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns sharedPrefs
    }

    private val hexagonApiClient = mockk<HexagonApiClient>()
    private val versionApiClient = mockk<VersionApiClient>()

    private val cacheService = spyk(CacheService(context))
    private val tokenService = mockk<TokenService>(relaxed = true)

    private val realRepository = HexagonRepository(hexagonApiClient, versionApiClient, cacheService, tokenService)
    private val repository = spyk(realRepository, recordPrivateCalls = true)

    @Test
    fun `getCountriesWithCities returns cached data when versions match`() = runTest {
        val key = "get-countries-with-cities"
        val version = "1"
        val cachedDto = listOf(GetCountriesWithCitiesResponseDto("Poland", listOf("Poznań")))

        val jsonFormat = Json { ignoreUnknownKeys = true }
        val cachedJson = jsonFormat.encodeToString(cachedDto)

        val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
        every { sharedPrefs.getString("$key.version", null) } returns version
        every { sharedPrefs.getString("$key.data", null) } returns cachedJson

        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs

        val realCacheService = CacheService(context)

        val versionResponseDto =
            GetCurrentVersionResponseDto(version = "1")
        val successResponse = Response.success(versionResponseDto)

        coEvery { versionApiClient.getCurrentVersion(key) } returns successResponse

        val repository = HexagonRepository(
            hexagonApiClient = hexagonApiClient,
            versionApiClient = versionApiClient,
            cacheService = realCacheService,
            tokenService = tokenService
        )

        val result = repository.getCountriesWithCities(forceRefresh = false)

        assertEquals(cachedDto, result)
        coVerify(exactly = 0) { hexagonApiClient.getCountriesWithCities() }
    }

    @Test
    fun `getCountriesWithCities fetches from API and saves to cache when versions do not match`() = runTest {
        val key = "get-countries-with-cities"
        val oldVersion = "1"
        val newVersion = "2"
        val networkData = listOf(GetCountriesWithCitiesResponseDto("Poland", listOf("Poznań")))

        every { sharedPrefs.getString(any(), any()) } returns oldVersion

        coEvery { versionApiClient.getCurrentVersion(key) } returns Response.success(GetCurrentVersionResponseDto(newVersion))
        coEvery { hexagonApiClient.getCountriesWithCities() } returns Response.success(networkData)

        val result = repository.getCountriesWithCities(forceRefresh = false)

        assertEquals(networkData, result)

        coVerify(exactly = 1) { hexagonApiClient.getCountriesWithCities() }

        verify {
            editor.putString(
                match { it.contains(key) },
                any()
            )
        }
        verify { editor.apply() }
    }

    @Test
    fun `getCountriesWithCities fetches from API when forceRefresh is true even if versions match`() = runTest {
        val key = "get-countries-with-cities"
        val version = "1"
        val networkData = listOf(GetCountriesWithCitiesResponseDto("Poland", listOf("Poznań")))

        every { sharedPrefs.getString(match { it.endsWith("version") }, any()) } returns version

        coEvery { versionApiClient.getCurrentVersion(key) } returns Response.success(GetCurrentVersionResponseDto(version))
        coEvery { hexagonApiClient.getCountriesWithCities() } returns Response.success(networkData)

        val result = repository.getCountriesWithCities(forceRefresh = true)

        assertEquals(networkData, result)

        coVerify(exactly = 1) { hexagonApiClient.getCountriesWithCities() }

        verify {
            editor.putString(
                match { it.contains(key) },
                any()
            )
        }
        verify { editor.apply() }
    }

    @Test
    fun `getCountriesWithCities returns fallback cached data when API fails`() = runTest {
        val key = "get-countries-with-cities"
        val cachedData = listOf(GetCountriesWithCitiesResponseDto("Poland", listOf("Poznań")))

        coEvery { versionApiClient.getCurrentVersion(key) } throws RuntimeException("Network Error")

        val jsonString = Json.encodeToString(cachedData)

        every { sharedPrefs.getString(match { it.contains(key) }, null) } returns jsonString

        val result = repository.getCountriesWithCities(forceRefresh = false)

        assertEquals(cachedData, result)

        verify(exactly = 0) { editor.putString(any(), any()) }
    }

    @Test
    fun `getCountriesWithCities throws exception when API fails and no cache is available`() = runTest {
        val key = "get-countries-with-cities"

        coEvery { versionApiClient.getCurrentVersion(key) } throws RuntimeException("Network connection lost")


        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getString(any(), any()) } returns null

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs

        val realCacheService = CacheService(mockContext)

        val testRepository = HexagonRepository(
            hexagonApiClient,
            versionApiClient,
            realCacheService,
            tokenService
        )

        val exception = assertThrows(RuntimeException::class.java) {
            runBlocking {
                testRepository.getCountriesWithCities(forceRefresh = false)
            }
        }

        assertEquals("Network connection lost", exception.message)
    }

    @Test
    fun `getHexagonsFromCity fetches from API and saves when versions mismatch`() = runTest {
        val city = "Poznań"
        val key = "get-hexagons-from-city-$city"
        val newVersion = "2"
        val networkData = GetHexagonsFromCityResponseDto(listOf(1.0, 1.0, 1.0, 1.0), listOf(Hexagon("x", listOf(listOf(1.0, 1.0)), listOf(1.0, 1.0), 1.0, 1.0)))

        coEvery { versionApiClient.getCurrentVersion(key) } returns Response.success(GetCurrentVersionResponseDto(newVersion))
        coEvery { hexagonApiClient.getHexagonsFromCity(city) } returns Response.success(networkData)

        every { sharedPrefs.getString(match { it.endsWith("version") && it.contains(city) }, any()) } returns "0"

        val result = repository.getHexagonsFromCity(city, forceRefresh = false)

        assert(result is HexagonRepository.GetHexagonsFromCityResult.Success)
        assertEquals(networkData, (result as HexagonRepository.GetHexagonsFromCityResult.Success).data)

        verify {
            editor.putString(
                match { it.contains(key) },
                any()
            )
        }
        verify { editor.apply() }
    }

    @Test
    fun `getHexagonsFromCity returns cached data when versions match`() = runTest {
        val city = "Poznań"
        val key = "get-hexagons-from-city-$city"
        val version = "1"
        val cachedData =
            GetHexagonsFromCityResponseDto(listOf(1.0, 1.0, 1.0, 1.0), listOf(Hexagon("x", listOf(listOf(1.0, 1.0)), listOf(1.0, 1.0), 1.0, 1.0)))

        val jsonString = Json.encodeToString(cachedData)

        coEvery { versionApiClient.getCurrentVersion(key) } returns Response.success(GetCurrentVersionResponseDto(version))
        every { sharedPrefs.getString(match { it.endsWith("version") }, any()) } returns version

        every { sharedPrefs.getString(match { it.contains("data") && it.contains(city) }, any()) } returns jsonString

        val result = repository.getHexagonsFromCity(city, forceRefresh = false)

        assert(result is HexagonRepository.GetHexagonsFromCityResult.Success)
        assertEquals(cachedData, (result as HexagonRepository.GetHexagonsFromCityResult.Success).data)

        coVerify(exactly = 0) { hexagonApiClient.getHexagonsFromCity(any()) }
    }

    @Test
    fun `getHexagonsFromCity returns Fallback with cached data when API fails`() = runTest {
        val city = "Warszawa"
        val key = "get-hexagons-from-city-$city"
        val cachedData =
            GetHexagonsFromCityResponseDto(listOf(1.0, 1.0, 1.0, 1.0), listOf(Hexagon("x", listOf(listOf(1.0, 1.0)), listOf(1.0, 1.0), 1.0, 1.0)))
        val jsonString = Json.encodeToString(cachedData)

        coEvery { versionApiClient.getCurrentVersion(key) } throws RuntimeException("Network Down")

        every { sharedPrefs.getString(match { it.contains("data") && it.contains(city) }, any()) } returns jsonString

        val result = repository.getHexagonsFromCity(city, forceRefresh = false)

        assert(result is HexagonRepository.GetHexagonsFromCityResult.Fallback)
        assertEquals(cachedData, (result as HexagonRepository.GetHexagonsFromCityResult.Fallback).data)
    }

    @Test
    fun `getHexagonsFromCity throws exception when API fails and cache is empty`() = runTest {
        val city = "EmptyCity"
        val key = "get-hexagons-from-city-$city"

        coEvery { versionApiClient.getCurrentVersion(key) } throws RuntimeException("Critical Failure")

        every { sharedPrefs.getString(any(), any()) } returns null

        val exception = assertThrows(RuntimeException::class.java) {
            runBlocking {
                repository.getHexagonsFromCity(city)
            }
        }

        assertEquals("Critical Failure", exception.message)
    }

    @Test
    fun `getPoisFromHexagon returns list of POIs when API call is successful`() = runTest {
        val hexagonId = "891f062c527ffff"
        val expectedPois = listOf(
            GetPoisFromHexagonResponseDto("Park1", "Nature", false),
            GetPoisFromHexagonResponseDto("Park2", "Nature", false),
        )

        coEvery { hexagonApiClient.getPoisFromHexagon(hexagonId) } returns Response.success(expectedPois)

        val result = repository.getPoisFromHexagon(hexagonId)

        assertEquals(expectedPois, result)
        coVerify(exactly = 1) { hexagonApiClient.getPoisFromHexagon(hexagonId) }
    }

    @Test
    fun `getPoisFromHexagon throws exception when API fails`() = runTest {
        val hexagonId = "invalid-hex"
        val errorCode = 500

        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        coEvery { hexagonApiClient.getPoisFromHexagon(hexagonId) } returns Response.error(errorCode, errorBody)

        val exception = assertThrows(Exception::class.java) {
            runBlocking {
                repository.getPoisFromHexagon(hexagonId)
            }
        }

        assertEquals("Failed to fetch POIs: $errorCode.", exception.message)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `postLocationBatch returns true, saves new token and emits updates on success`() = runTest {
        val validToken = "valid_token"
        val newToken = "refreshed_token"
        val requestDto = PostLocationBatchRequestDto(locations = emptyList())

        val updates = listOf(HexagonProgress(hexagonId = "hex1", progress = 1.0))
        val responseBody = PostLocationBatchResponseDto(updatedHexagons = updates, token = newToken)

        every { tokenService.getToken() } returns validToken

        coEvery {
            hexagonApiClient.postLocationBatch("Bearer $validToken", requestDto)
        } returns Response.success(responseBody)

        val emittedValues = mutableListOf<List<HexagonProgress>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.hexagonUpdates.collect { emittedValues.add(it) }
        }

        val result = repository.postLocationBatch(requestDto)

        assertTrue(result)

        verify(exactly = 1) { tokenService.saveToken(newToken) }

        assertEquals(1, emittedValues.size)
        assertEquals(updates, emittedValues[0])

        job.cancel()
    }

    @Test
    fun `postLocationBatch throws InvalidTokenException when token is missing`() = runTest {
        val requestDto = PostLocationBatchRequestDto(locations = emptyList())

        every { tokenService.getToken() } returns null

        assertThrows(InvalidTokenException::class.java) {
            runBlocking {
                repository.postLocationBatch(requestDto)
            }
        }

        coVerify(exactly = 0) { hexagonApiClient.postLocationBatch(any(), any()) }
    }

    @Test
    fun `postLocationBatch returns false when API fails`() = runTest {
        val token = "abc"
        val requestDto = PostLocationBatchRequestDto(locations = emptyList())

        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        every { tokenService.getToken() } returns token

        coEvery {
            hexagonApiClient.postLocationBatch("Bearer $token", requestDto)
        } returns Response.error(500, errorBody)

        val result = repository.postLocationBatch(requestDto)

        assertFalse(result)

        verify(exactly = 0) { tokenService.saveToken(any()) }
    }

    @Test
    fun `postLocationBatch returns false when exception is thrown during API call`() = runTest {
        val token = "abc"
        val requestDto = PostLocationBatchRequestDto(emptyList())

        every { tokenService.getToken() } returns token

        coEvery {
            hexagonApiClient.postLocationBatch(any(), any())
        } throws RuntimeException("No internet")

        val result = repository.postLocationBatch(requestDto)

        assertFalse(result)
    }

    @Test
    fun `getHexagonProgresses returns list when API call is successful`() = runTest {
        val city = "Poznań"
        val token = "valid_token"
        val expectedProgress = listOf(HexagonProgress(hexagonId = "hex1", progress = 0.8))

        every { tokenService.getToken() } returns token

        coEvery {
            hexagonApiClient.getHexagonProgresses("Bearer $token", city)
        } returns Response.success(expectedProgress)

        val result = repository.getHexagonProgresses(city)

        assertEquals(expectedProgress, result)
    }

    @Test
    fun `getHexagonProgresses throws InvalidTokenException when local token is missing`() = runTest {
        val city = "Poznań"

        every { tokenService.getToken() } returns null

        assertThrows(InvalidTokenException::class.java) {
            runBlocking {
                repository.getHexagonProgresses(city)
            }
        }

        coVerify(exactly = 0) { hexagonApiClient.getHexagonProgresses(any(), any()) }
    }

    @Test
    fun `getHexagonProgresses clears token and throws InvalidTokenException on API 401 error`() = runTest {
        val city = "Warszawa"
        val token = "expired_token"
        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        every { tokenService.getToken() } returns token

        coEvery {
            hexagonApiClient.getHexagonProgresses("Bearer $token", city)
        } returns Response.error(401, errorBody)

        assertThrows(InvalidTokenException::class.java) {
            runBlocking {
                repository.getHexagonProgresses(city)
            }
        }

        verify(exactly = 1) { tokenService.clearToken() }
    }

    @Test
    fun `getHexagonProgresses throws generic Exception on other API errors`() = runTest {
        val city = "Berlin"
        val token = "valid_token"
        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        every { tokenService.getToken() } returns token

        coEvery {
            hexagonApiClient.getHexagonProgresses("Bearer $token", city)
        } returns Response.error(500, errorBody)

        val exception = assertThrows(Exception::class.java) {
            runBlocking {
                repository.getHexagonProgresses(city)
            }
        }

        assert(exception.message!!.contains("API Error: 500"))

        verify(exactly = 0) { tokenService.clearToken() }
    }

    @Test
    fun `generateRoute calls API, updates token and returns result when successful`() = runTest {
        val token = "valid_token"
        val newToken = "refreshed_token"
        val jobId = "job-123"
        val lat = 52.0
        val lon = 16.0
        val duration = 60

        val expectedResult = WorkerResult(emptyList())

        val apiResponse = GenerateRouteResponseDto(jobId = jobId, token = newToken)

        every { tokenService.getToken() } returns token

        coEvery {
            hexagonApiClient.generateRoute(
                "Bearer $token",
                match { it.userLatitude == lat && it.duration == duration }
            )
        } returns Response.success(apiResponse)

        coEvery {
            repository["listenForRouteCompletion"](jobId, token)
        } returns expectedResult

        val result = repository.generateRoute(lat, lon, duration)

        assertEquals(expectedResult, result)

        verify(exactly = 1) { tokenService.saveToken(newToken) }
    }

    @Test
    fun `generateRoute returns null when local token is missing`() = runTest {
        every { tokenService.getToken() } returns null

        val result = repository.generateRoute(52.0, 16.0, 60)

        assertNull(result)

        coVerify(exactly = 0) { hexagonApiClient.generateRoute(any(), any()) }
    }

    @Test
    fun `generateRoute clears token and returns null on API 401 error`() = runTest {
        val token = "expired_token"
        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        every { tokenService.getToken() } returns token

        coEvery {
            hexagonApiClient.generateRoute("Bearer $token", any())
        } returns Response.error(401, errorBody)

        val result = repository.generateRoute(52.0, 16.0, 60)

        assertNull(result)
        verify(exactly = 1) { tokenService.clearToken() }
    }

    @Test
    fun `generateRoute throws exception on generic API error`() = runTest {
        val token = "valid_token"
        val errorBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())

        every { tokenService.getToken() } returns token

        coEvery {
            hexagonApiClient.generateRoute("Bearer $token", any())
        } returns Response.error(500, errorBody)

        val exception = assertThrows(Exception::class.java) {
            runBlocking {
                repository.generateRoute(52.0, 16.0, 60)
            }
        }

        assert(exception.message!!.contains("API Error: 500"))
    }

    @Test
    fun `generateRoute throws exception when listening times out or fails`() = runTest {
        val token = "valid_token"
        val jobId = "job-123"
        val apiResponse = GenerateRouteResponseDto(jobId = jobId, token = null)

        every { tokenService.getToken() } returns token

        coEvery {
            hexagonApiClient.generateRoute(any(), any())
        } returns Response.success(apiResponse)

        coEvery {
            repository["listenForRouteCompletion"](jobId, token)
        } throws RuntimeException("Job failed or timed out")

        val exception = assertThrows(RuntimeException::class.java) {
            runBlocking {
                repository.generateRoute(52.0, 16.0, 60)
            }
        }

        assertEquals("Job failed or timed out", exception.message)
    }
}