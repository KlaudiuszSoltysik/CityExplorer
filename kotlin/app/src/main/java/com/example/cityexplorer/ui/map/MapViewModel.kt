package com.example.cityexplorer.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.api.ApiClient
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.repository.HexagonRepository
import kotlinx.coroutines.launch
import android.location.Location
import com.example.cityexplorer.data.repository.getLocationFlow
import com.google.android.gms.location.FusedLocationProviderClient

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(val data: GetCityHexagonsDataDto) : MainUiState
    data class Error(val message: String) : MainUiState
}

class MapViewModel(
    private val city: String,
    private val mode: String,
    private val locationClient: FusedLocationProviderClient
) : ViewModel() {
    private val repository = HexagonRepository(ApiClient.hexagonApiService)

    var uiState: MainUiState by mutableStateOf(MainUiState.Loading)
        private set

    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    var userLocation: Location? by mutableStateOf(null)
        private set

    init {
        loadData(isInitial = true)
    }

    fun refreshData() {
        loadData(isInitial = false)
    }

    private fun loadData(isInitial: Boolean) {
        viewModelScope.launch {
            if (isInitial) uiState = MainUiState.Loading else isRefreshing = true

            try {
                val data = repository.getHexagonsFromCity(city, mode)
                uiState = MainUiState.Success(data)
            } catch (e: Exception) {
                if (isInitial) uiState = MainUiState.Error(e.message ?: "Unknown error")
            } finally {
                isRefreshing = false
            }
        }
    }

    fun startLocationTracking() {
        viewModelScope.launch {
            try {
                getLocationFlow(locationClient).collect { location ->
                    userLocation = location
                }
            } catch (e: Exception) {
                uiState = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class MapViewModelFactory(
    private val city: String,
    private val mode: String,
    private val locationClient: FusedLocationProviderClient
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(city, mode, locationClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}