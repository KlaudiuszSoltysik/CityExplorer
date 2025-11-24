package com.example.cityexplorer.ui.cityselector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.api.ApiClient
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.repository.HexagonRepository
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

    init {
        fetchData()
    }

    private fun fetchData() {
        viewModelScope.launch {
            uiState = MainUiState.Loading
            try {
                val data = repository.getCountriesWithCities()
                println("Data fetched successfully: ${data.size} items")
                uiState = MainUiState.Success(data)
            } catch (e: Exception) {
                println("Error fetching data: ${e.message}")
                uiState = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
