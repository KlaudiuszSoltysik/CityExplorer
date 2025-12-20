package com.example.cityexplorer.ui.cityselector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CitySelectorUiState {
    data object Loading : CitySelectorUiState
    data class Success(val countriesWithCities: List<GetCountriesWithCitiesDto>) : CitySelectorUiState
    data class Error(val message: String) : CitySelectorUiState
}

@HiltViewModel
class CitySelectorViewModel @Inject constructor(
    private val hexagonRepository: HexagonRepository
) : ViewModel() {
    var uiState: CitySelectorUiState by mutableStateOf(CitySelectorUiState.Loading)
        private set

    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    init {
        loadData(false)
    }

    fun refreshData() {
        loadData(true)
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
