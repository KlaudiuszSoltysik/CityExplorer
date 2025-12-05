package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.HexagonApiClient
import com.example.cityexplorer.data.api.VersionApiClient
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.dtos.GetPoisFromHexagonDto
import com.example.cityexplorer.data.dtos.PostLocationBatchDto
import com.example.cityexplorer.data.util.CacheService
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HexagonRepository(
    private val hexagonApiClient: HexagonApiClient,
    private val versionApiClient: VersionApiClient,
    private val cacheService: CacheService
) {
    private val countriesKey = "get-countries-with-cities"

    suspend fun getCountriesWithCities(forceRefresh: Boolean = false): List<GetCountriesWithCitiesDto> = withContext(Dispatchers.IO) {
        val dtoType = object : TypeToken<List<GetCountriesWithCitiesDto>>() {}.type

        try {
            val remoteVersion = versionApiClient.getCurrentVersion(countriesKey).string()
            val cachedVersion = cacheService.getCachedVersion(countriesKey)

            if (!forceRefresh && cachedVersion == remoteVersion) {
                val cachedData = cacheService.getCachedData<List<GetCountriesWithCitiesDto>>(countriesKey, dtoType)
                if (cachedData != null) return@withContext cachedData
            }

            val networkData = hexagonApiClient.getCountriesWithCities()

            cacheService.saveToCache(countriesKey, remoteVersion, networkData)

            return@withContext networkData

        } catch (e: Exception) {
            val fallbackData = cacheService.getCachedData<List<GetCountriesWithCitiesDto>>(countriesKey, dtoType)

            if (fallbackData != null) {
                return@withContext fallbackData
            } else {
                throw e
            }
        }
    }

    suspend fun getHexagonsFromCity(city: String, forceRefresh: Boolean = false): RepoResult<GetCityHexagonsDataDto> = withContext(Dispatchers.IO) {
        val key = "get-hexagons-from-city-$city"
        val dtoType = object : TypeToken<GetCityHexagonsDataDto>() {}.type

        try {
            val remoteVersion = versionApiClient.getCurrentVersion(key).string()
            val cachedVersion = cacheService.getCachedVersion(key)

            if (!forceRefresh && cachedVersion == remoteVersion) {
                val cachedData = cacheService.getCachedData<GetCityHexagonsDataDto>(key, dtoType)
                if (cachedData != null) return@withContext RepoResult.Success(cachedData)
            }

            val networkData = hexagonApiClient.getHexagonsFromCity(city)
            cacheService.saveToCache(key, remoteVersion, networkData)

            return@withContext RepoResult.Success(networkData)

        } catch (e: Exception) {
            val fallbackData = cacheService.getCachedData<GetCityHexagonsDataDto>(key, dtoType)

            if (fallbackData != null) {
                return@withContext RepoResult.Fallback(fallbackData)
            } else {
                throw e
            }
        }
    }

    suspend fun getPoisFromHexagon(hexagonId: String): List<GetPoisFromHexagonDto> {
        return hexagonApiClient.getPoisFromHexagon(hexagonId)
    }

    suspend fun postLocationBatch(locationDto: PostLocationBatchDto): Boolean {
          try {
            val response = hexagonApiClient.postLocationBatch(locationDto)

            if (response.isSuccessful) {
                val responseBody = response.body()
                val changes = responseBody?.updatedHexagons ?: emptyList()
                // TODO: Zapisz 'changes' do lokalnej bazy danych (Room)

                return true
            } else {
                return false
            }
        } catch (_: Exception) {
            return false
        }
    }

    sealed interface RepoResult<out T> {
        data class Success<T>(val data: T) : RepoResult<T>
        data class Fallback<T>(val data: T) : RepoResult<T>
    }
}