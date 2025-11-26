package com.example.cityexplorer.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.api.ApiClient
import com.example.cityexplorer.data.dtos.GetHexagonsFromCityDto
import com.example.cityexplorer.data.repository.HexagonRepository
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(val hexagons: List<GetHexagonsFromCityDto>) : MainUiState
    data class Error(val message: String) : MainUiState
}

class MapViewModel(private val city: String, private val mode: String) : ViewModel() {
    private val repository = HexagonRepository(ApiClient.hexagonApiService)

    var uiState: MainUiState by mutableStateOf(MainUiState.Loading)
        private set

    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                var data = repository.getHexagonsFromCity(city, mode)

                data = data.map { hex ->
                    val closedBoundaries: List<List<Double>> = hex.boundaries + listOf(hex.boundaries.first())
                    hex.copy(boundaries = closedBoundaries)
                }

                uiState = MainUiState.Success(data.take(10))
            } catch (e: Exception) {
                uiState = MainUiState.Error(e.message ?: "Unknown error")
            } finally {
                isRefreshing = false
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class MapViewModelFactory(
    private val city: String,
    private val mode: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(city, mode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}