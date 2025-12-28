package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.BuildConfig
import com.example.cityexplorer.data.api.HexagonApiClient
import com.example.cityexplorer.data.api.VersionApiClient
import com.example.cityexplorer.data.dtos.GenerateRouteRequestDto
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.dtos.GetPoisFromHexagonDto
import com.example.cityexplorer.data.dtos.HexagonProgressDto
import com.example.cityexplorer.data.dtos.PostLocationBatchDto
import com.example.cityexplorer.data.dtos.WorkerResultDto
import com.example.cityexplorer.data.util.CacheService
import com.example.cityexplorer.data.util.TokenService
import com.google.gson.reflect.TypeToken
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HexagonRepository @Inject constructor(
    private val hexagonApiClient: HexagonApiClient,
    private val versionApiClient: VersionApiClient,
    private val cacheService: CacheService,
    private val tokenService: TokenService
) {
    private val _hexagonUpdates = MutableSharedFlow<List<HexagonProgressDto>>()
    val hexagonUpdates = _hexagonUpdates.asSharedFlow()

    suspend fun getCountriesWithCities(forceRefresh: Boolean = false): List<GetCountriesWithCitiesDto> =
        withContext(Dispatchers.IO) {
            val key = "get-countries-with-cities"
            val dtoType = object : TypeToken<List<GetCountriesWithCitiesDto>>() {}.type

            try {
                val remoteVersion = versionApiClient.getCurrentVersion(key).string()
                val cachedVersion = cacheService.getCachedVersion(key)

                if (!forceRefresh && cachedVersion == remoteVersion) {
                    val cachedData =
                        cacheService.getCachedData<List<GetCountriesWithCitiesDto>>(key, dtoType)
                    if (cachedData != null) return@withContext cachedData
                }

                val networkData = hexagonApiClient.getCountriesWithCities()

                cacheService.saveToCache(key, remoteVersion, networkData)

                return@withContext networkData

            } catch (e: Exception) {
                val fallbackData =
                    cacheService.getCachedData<List<GetCountriesWithCitiesDto>>(key, dtoType)

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
    ): GetHexagonsFromCityResult<GetCityHexagonsDataDto> = withContext(Dispatchers.IO) {
        val key = "get-hexagons-from-city-$city"
        val dtoType = object : TypeToken<GetCityHexagonsDataDto>() {}.type

        try {
            val remoteVersion = versionApiClient.getCurrentVersion(key).string()
            val cachedVersion = cacheService.getCachedVersion(key)

            if (!forceRefresh && cachedVersion == remoteVersion) {
                val cachedData = cacheService.getCachedData<GetCityHexagonsDataDto>(key, dtoType)
                if (cachedData != null) return@withContext GetHexagonsFromCityResult.Success(
                    cachedData
                )
            }

            val networkData = hexagonApiClient.getHexagonsFromCity(city)
            cacheService.saveToCache(key, remoteVersion, networkData)

            return@withContext GetHexagonsFromCityResult.Success(networkData)

        } catch (e: Exception) {
            val fallbackData = cacheService.getCachedData<GetCityHexagonsDataDto>(key, dtoType)

            if (fallbackData != null) {
                return@withContext GetHexagonsFromCityResult.Fallback(fallbackData)
            } else {
                throw e
            }
        }
    }

    suspend fun getPoisFromHexagon(hexagonId: String): List<GetPoisFromHexagonDto> {
        return hexagonApiClient.getPoisFromHexagon(hexagonId)
    }

    suspend fun postLocationBatch(locationDtos: PostLocationBatchDto): Boolean {
        val token = tokenService.getToken()

        if (token.isNullOrBlank()) {
            throw InvalidTokenException()
        }

        try {
            val response = hexagonApiClient.postLocationBatch("Bearer $token", locationDtos)

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

    suspend fun getHexagonProgresses(city: String): List<HexagonProgressDto> {
        val token = tokenService.getToken()

        if (token.isNullOrBlank()) {
            throw InvalidTokenException()
        }

        return try {
            hexagonApiClient.getHexagonProgresses("Bearer $token", city)

        } catch (e: HttpException) {
            if (e.code() == 401) {
                tokenService.clearToken()
                throw InvalidTokenException()
            } else {
                throw e
            }
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun generateRoute(startHexId: String, duration: Int): WorkerResultDto {
        val token = tokenService.getToken()

        if (token.isNullOrBlank()) {
            throw InvalidTokenException()
        }

        try {
            val response = hexagonApiClient.generateRoute(
                "Bearer $token",
                GenerateRouteRequestDto(startHexId, duration)
            )

            if (response.isSuccessful) {
                val responseBody = response.body() ?: throw Exception()

                if (responseBody.token != null) {
                    tokenService.saveToken(responseBody.token)
                }

                val jobId = responseBody.jobId

                val token = tokenService.getToken()

                if (token.isNullOrBlank()) {
                    throw InvalidTokenException()
                }

                return withTimeout(10000L) {
                    listenForRouteCompletion(jobId, token)
                }

            } else {
                throw Exception("Failed to generate route.")
            }
        } catch (_: Exception) {
            throw Exception("Failed to generate route.")
        }
    }

    private suspend fun listenForRouteCompletion(jobId: String, token: String): WorkerResultDto =
        suspendCancellableCoroutine { continuation ->
            val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
            val hubUrl = "$baseUrl/hubs/worker"

            val hubConnection = HubConnectionBuilder.create(hubUrl)
                .withAccessTokenProvider(Single.just(token))
                .withHeader("X-Api-Key", BuildConfig.API_KEY)
                .build()

            hubConnection.on("JobCompleted", { result: WorkerResultDto ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
                hubConnection.stop()
            }, WorkerResultDto::class.java)

            hubConnection.on("JobFailed", { reason: String ->
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception("Job failed: $reason"))
                }
                hubConnection.stop()
            }, String::class.java)

            continuation.invokeOnCancellation {
                if (hubConnection.connectionState != HubConnectionState.DISCONNECTED) {
                    hubConnection.stop()
                }
            }

            try {
                hubConnection.start().blockingAwait()

                hubConnection.send("JoinJobGroup", jobId)

            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
                hubConnection.stop()
            }
        }

    sealed interface GetHexagonsFromCityResult<out T> {
        data class Success<T>(val data: T) : GetHexagonsFromCityResult<T>
        data class Fallback<T>(val data: T) : GetHexagonsFromCityResult<T>
    }
}

class InvalidTokenException : Exception("Token is missing or invalid.")