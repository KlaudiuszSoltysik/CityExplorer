package com.example.cityexplorer.ui.cityselector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesResponseDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CitySelectorUiState {
    data object Loading : CitySelectorUiState
    data class Success(val data: List<GetCountriesWithCitiesResponseDto>) : CitySelectorUiState
    data class Error(val message: String) : CitySelectorUiState
}

@HiltViewModel
class CitySelectorViewModel @Inject constructor(
    private val hexagonRepository: HexagonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<CitySelectorUiState>(CitySelectorUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        loadData(false)
    }

    fun refreshData() {
        loadData(true)
    }

    private fun loadData(forceRefresh: Boolean) {
        viewModelScope.launch {
            if (!forceRefresh) {
                _uiState.value = CitySelectorUiState.Loading
            } else {
                _isRefreshing.value = true
            }

            try {
                val data = hexagonRepository.getCountriesWithCities(forceRefresh)
                _uiState.value = CitySelectorUiState.Success(data)
            } catch (e: Exception) {
                _uiState.value = CitySelectorUiState.Error(e.message ?: "Unknown error.")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
