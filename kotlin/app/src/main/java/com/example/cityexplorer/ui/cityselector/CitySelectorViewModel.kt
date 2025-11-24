package com.example.cityexplorer.ui.cityselector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.api.ApiClient
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(val countriesWithCities: List<GetCountriesWithCitiesDto>) : MainUiState
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
                val countriesWithCities = ApiClient.retrofit.getCountriesWithCities()
                uiState = MainUiState.Success(countriesWithCities)
            } catch (e: Exception) {
                uiState = MainUiState.Error(e.message ?: "Unknown error.")
            }
        }
    }
}
