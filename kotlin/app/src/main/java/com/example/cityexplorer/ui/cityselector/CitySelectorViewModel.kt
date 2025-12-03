package com.example.cityexplorer.ui.cityselector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.api.ApiClient
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.repositories.VersionRepository
import com.example.cityexplorer.data.util.CacheService
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(val countriesWithCities: List<GetCountriesWithCitiesDto>) : MainUiState
    data class Error(val message: String) : MainUiState
}

class CitySelectorViewModel(private val cacheService: CacheService) : ViewModel() {
    private val hexagonRepository = HexagonRepository(ApiClient.hexagonApiService)
    private val versionRepository = VersionRepository(ApiClient.versionApiService)

    var uiState: MainUiState by mutableStateOf(MainUiState.Loading)
        private set

    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    init {
        loadData(isInitial = true)
    }

    fun refreshData() {
        loadData(isInitial = false)
    }

    fun loadData(isInitial: Boolean) {
        viewModelScope.launch {
            if (isInitial) uiState = MainUiState.Loading else isRefreshing = true
            val key = "get-countries-with-cities"

            val dtoType = object : TypeToken<List<GetCountriesWithCitiesDto>>() {}.type

            try {
                val remoteVersion = versionRepository.getCurrentVersion(key)
                val cachedVersion = cacheService.getCachedVersion(key)

                val cachedData = if (cachedVersion == remoteVersion) {
                    cacheService.getCachedData<List<GetCountriesWithCitiesDto>>(key, dtoType)
                } else {
                    null
                }

                val data = if (cachedData != null) {
                    cachedData
                } else {
                    val networkData = hexagonRepository.getCountriesWithCities()
                    cacheService.saveToCache(key, remoteVersion, networkData)
                    networkData
                }

                uiState = MainUiState.Success(data)
            } catch (_: Exception) {

                val fallbackData = cacheService.getCachedData<List<GetCountriesWithCitiesDto>>(key, dtoType)

                if (fallbackData != null) {
                    uiState = MainUiState.Success(fallbackData)
                } else {
                    if (isInitial) uiState = MainUiState.Error("Couldn't load data.")
                }
            } finally {
                isRefreshing = false
            }
        }
    }
}

class CitySelectorViewModelFactory(private val cacheService: CacheService) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CitySelectorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CitySelectorViewModel(cacheService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}