package com.example.cityexplorer.ui.cityselector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.api.ApiClient
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.util.HexagonRepository
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(val countriesWithCities: List<GetCountriesWithCitiesDto>) : MainUiState
    data class Error(val message: String) : MainUiState
}

class CitySelectorViewModel : ViewModel() {
    private val repository = HexagonRepository(ApiClient.hexagonApiService)

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

    private fun loadData(isInitial: Boolean) {
        viewModelScope.launch {
            if (isInitial) uiState = MainUiState.Loading else isRefreshing = true

            try {
                val data = repository.getCountriesWithCities()
                uiState = MainUiState.Success(data)
            } catch (e: Exception) {
                if (isInitial) uiState = MainUiState.Error(e.message ?: "Unknown error")
            } finally {
                isRefreshing = false
            }
        }
    }
}
