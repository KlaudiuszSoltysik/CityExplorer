package com.example.cityexplorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.ApiClient
import com.example.cityexplorer.data.GetCountriesWithCitiesDto
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(val cities: List<GetCountriesWithCitiesDto>) : MainUiState
    data class Error(val message: String) : MainUiState
}

class MainViewModel : ViewModel() {
    var uiState: MainUiState by mutableStateOf(MainUiState.Loading)
        private set

    init {
        fetchCountriesWithCities()
    }

    private fun fetchCountriesWithCities() {
        viewModelScope.launch {
            uiState = MainUiState.Loading
            try {
                val cities = ApiClient.retrofit.getCountriesWithCities()
                uiState = MainUiState.Success(cities)
            } catch (e: Exception) {
                uiState = MainUiState.Error(e.message ?: "Wystąpił nieznany błąd")
            }
        }
    }
}
