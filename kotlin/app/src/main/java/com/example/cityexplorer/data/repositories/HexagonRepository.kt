package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.BuildConfig
import com.example.cityexplorer.data.api.HexagonApiClient
import com.example.cityexplorer.data.api.VersionApiClient
import com.example.cityexplorer.data.dtos.GenerateRouteRequestDto
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesResponseDto
import com.example.cityexplorer.data.dtos.GetCurrentVersionRequestDto
import com.example.cityexplorer.data.dtos.GetHexagonsFromCityResponseDto
import com.example.cityexplorer.data.dtos.GetPoisFromHexagonResponseDto
import com.example.cityexplorer.data.dtos.HexagonProgress
import com.example.cityexplorer.data.dtos.PostLocationBatchRequestDto
import com.example.cityexplorer.data.dtos.WorkerResult
import com.example.cityexplorer.data.util.CacheService
import com.example.cityexplorer.data.util.TokenService
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class HexagonRepository @Inject constructor(
    private val hexagonApiClient: HexagonApiClient,
    private val versionApiClient: VersionApiClient,
    private val cacheService: CacheService,
    private val tokenService: TokenService
) {
    private val _hexagonUpdates = MutableSharedFlow<List<HexagonProgress>>()
    val hexagonUpdates = _hexagonUpdates.asSharedFlow()

    suspend fun getCountriesWithCities(forceRefresh: Boolean = false): List<GetCountriesWithCitiesResponseDto> =
        withContext(Dispatchers.IO) {
            val key = "get-countries-with-cities"

            try {
                val versionRequest = GetCurrentVersionRequestDto(key = key)
                val versionResponse = versionApiClient.getCurrentVersion(versionRequest.key)

                if (!versionResponse.isSuccessful || versionResponse.body() == null) {
                    throw Exception("Failed to fetch version: ${versionResponse.code()}.")
                }

                val remoteVersion = versionResponse.body()!!.version
                val cachedVersion = cacheService.getCachedVersion(key)

                if (!forceRefresh && cachedVersion == remoteVersion) {
                    val cachedData = cacheService.getCachedData<List<GetCountriesWithCitiesResponseDto>>(key)

                    if (cachedData != null) return@withContext cachedData
                }

                val response = hexagonApiClient.getCountriesWithCities()

                if (response.isSuccessful && response.body() != null) {
                    val networkData = response.body()!!

                    cacheService.saveToCache(key, remoteVersion, networkData)

                    return@withContext networkData
                } else {
                    throw Exception("API Error: ${response.code()} ${response.message()}.")
                }
            } catch (e: Exception) {
                val fallbackData = cacheService.getCachedData<List<GetCountriesWithCitiesResponseDto>>(key)

                if (fallbackData != null) {
                    return@withContext fallbackData
                } else {
                    throw e
                }
            }
        }

    suspend fun getHexagonsFromCity(
        city: String,
        forceRefresh: Boolean = false
    ): GetHexagonsFromCityResult<GetHexagonsFromCityResponseDto> = withContext(Dispatchers.IO) {
        val key = "get-hexagons-from-city-$city"

        try {
            val versionRequest = GetCurrentVersionRequestDto(key = key)
            val versionResponse = versionApiClient.getCurrentVersion(versionRequest.key)

            if (!versionResponse.isSuccessful || versionResponse.body() == null) {
                throw Exception("Version check failed: ${versionResponse.code()}.")
            }

            val remoteVersion = versionResponse.body()!!.version
            val cachedVersion = cacheService.getCachedVersion(key)

            if (!forceRefresh && cachedVersion == remoteVersion) {
                val cachedData = cacheService.getCachedData<GetHexagonsFromCityResponseDto>(key)

                if (cachedData != null) {
                    return@withContext GetHexagonsFromCityResult.Success(cachedData)
                }
            }

            val response = hexagonApiClient.getHexagonsFromCity(city)

            if (response.isSuccessful && response.body() != null) {
                val networkData = response.body()!!

                cacheService.saveToCache(key, remoteVersion, networkData)

                return@withContext GetHexagonsFromCityResult.Success(networkData)
            } else {
                throw Exception("API Error: ${response.code()} ${response.message()}.")
            }

        } catch (e: Exception) {
            val fallbackData = cacheService.getCachedData<GetHexagonsFromCityResponseDto>(key)

            if (fallbackData != null) {
                return@withContext GetHexagonsFromCityResult.Fallback(fallbackData)
            } else {
                throw e
            }
        }
    }

    suspend fun getPoisFromHexagon(hexagonId: String): List<GetPoisFromHexagonResponseDto> {
        val response = hexagonApiClient.getPoisFromHexagon(hexagonId)

        if (response.isSuccessful && response.body() != null) {
            return response.body() ?: emptyList()
        } else {
            throw Exception("Failed to fetch POIs: ${response.code()}.")
        }
    }

    suspend fun postLocationBatch(requestDto: PostLocationBatchRequestDto): Boolean {
        val token = tokenService.getToken()

        if (token.isNullOrBlank()) {
            throw InvalidTokenException()
        }

        try {
            val response = hexagonApiClient.postLocationBatch("Bearer $token", requestDto)

            if (response.isSuccessful) {
                val responseBody = response.body()
                val changes = responseBody?.updatedHexagons ?: emptyList()

                if (changes.isNotEmpty()) {
                    _hexagonUpdates.emit(changes)
                }

                if (responseBody?.token != null) {
                    tokenService.saveToken(responseBody.token)
                }

                return true
            } else {
                return false
            }
        } catch (_: Exception) {
            return false
        }
    }

    suspend fun getHexagonProgresses(city: String): List<HexagonProgress> =
        withContext(Dispatchers.IO) {
            val token = tokenService.getToken()

            if (token.isNullOrBlank()) {
                throw InvalidTokenException()
            }

            try {
                val response = hexagonApiClient.getHexagonProgresses("Bearer $token", city)

                if (response.isSuccessful && response.body() != null) {
                    return@withContext response.body()!!
                } else {
                    if (response.code() == 401) {
                        tokenService.clearToken()
                        throw InvalidTokenException()
                    }

                    throw Exception("API Error: ${response.code()} ${response.message()}.")
                }
            } catch (e: Exception) {
                throw e
            }
        }

    suspend fun generateRoute(
        userLatitude: Double,
        userLongitude: Double,
        duration: Int
    ): WorkerResult? {
        val token = tokenService.getToken()

        if (token.isNullOrBlank()) {
            return null
        }

        try {
            val response = hexagonApiClient.generateRoute(
                "Bearer $token",
                GenerateRouteRequestDto(userLatitude, userLongitude, duration)
            )

            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body() ?: throw Exception()

                if (responseBody.token != null) {
                    tokenService.saveToken(responseBody.token)
                }

                val jobId = responseBody.jobId

                return withTimeout(10000L) {
                    listenForRouteCompletion(jobId, token)
                }
            } else {
                if (response.code() == 401) {
                    tokenService.clearToken()
                    return null
                }

                throw Exception("API Error: ${response.code()} ${response.message()}.")
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun listenForRouteCompletion(jobId: String, token: String): WorkerResult =
        suspendCancellableCoroutine { continuation ->
            val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
            val hubUrl = "$baseUrl/hubs/worker"

            val hubConnection = HubConnectionBuilder.create(hubUrl)
                .withAccessTokenProvider(Single.just(token))
                .withHeader("X-Api-Key", BuildConfig.API_KEY)
                .build()

            hubConnection.on("JobCompleted", { result: WorkerResult ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
                hubConnection.stop()
            }, WorkerResult::class.java)

            hubConnection.on("JobFailed", { reason: String ->
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception("Job failed: $reason."))
                }
                hubConnection.stop()
            }, String::class.java)

            hubConnection.onClosed { exception ->
                if (continuation.isActive && exception != null) {
                    continuation.resumeWithException(exception)
                }
            }

            val startDisposable = hubConnection.start().subscribe({
                try {
                    hubConnection.send("JoinJobGroup", jobId)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }, { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            })

            continuation.invokeOnCancellation {
                startDisposable.dispose()

                if (hubConnection.connectionState != HubConnectionState.DISCONNECTED) {
                    hubConnection.stop()
                }
            }
        }

    sealed interface GetHexagonsFromCityResult<out T> {
        data class Success<T>(val data: T) : GetHexagonsFromCityResult<T>
        data class Fallback<T>(val data: T) : GetHexagonsFromCityResult<T>
    }
}

class InvalidTokenException : Exception("Token is missing or invalid.")