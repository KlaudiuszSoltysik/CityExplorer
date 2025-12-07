package com.example.cityexplorer.ui.cityselector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import kotlinx.coroutines.launch

sealed interface CitySelectorUiState {
    data object Loading : CitySelectorUiState
    data class Success(val countriesWithCities: List<GetCountriesWithCitiesDto>) : CitySelectorUiState
    data class Error(val message: String) : CitySelectorUiState
}

class CitySelectorViewModel(
    private val hexagonRepository: HexagonRepository
) : ViewModel() {
    var uiState: CitySelectorUiState by mutableStateOf(CitySelectorUiState.Loading)
        private set

    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    init {
        loadData(forceRefresh = false)
    }

    fun refreshData() {
        loadData(forceRefresh = true)
    }

    private fun loadData(forceRefresh: Boolean) {
        viewModelScope.launch {
            if (!forceRefresh) uiState = CitySelectorUiState.Loading else isRefreshing = true

            try {
                val data = hexagonRepository.getCountriesWithCities(forceRefresh)
                uiState = CitySelectorUiState.Success(data)
            } catch (_: Exception) {
                uiState = CitySelectorUiState.Error("Couldn't load data")
            } finally {
                isRefreshing = false
            }
        }
    }
}

class CitySelectorViewModelFactory(private val hexagonRepository: HexagonRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CitySelectorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CitySelectorViewModel(hexagonRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}