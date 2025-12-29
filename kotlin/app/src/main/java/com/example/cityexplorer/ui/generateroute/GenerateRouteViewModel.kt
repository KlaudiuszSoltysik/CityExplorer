package com.example.cityexplorer.ui.generateroute

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.dtos.WorkerResult
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.TokenService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface GenerateRouteUiEvent {
    data object NavigateToLogin : GenerateRouteUiEvent
}

sealed interface GenerateRouteUiState {
    data object Choose : GenerateRouteUiState
    data object Loading : GenerateRouteUiState
    data class Success(val data: WorkerResult) : GenerateRouteUiState
    data class Error(val message: String) : GenerateRouteUiState
}

@HiltViewModel
class GenerateRouteViewModel @Inject constructor(
    private val tokenService: TokenService,
    private val hexagonRepository: HexagonRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<GenerateRouteUiState>(GenerateRouteUiState.Choose)
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = Channel<GenerateRouteUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val availableTimes = (30..300 step 30).toList()

    private val _selectedTime = MutableStateFlow(availableTimes.first())
    val selectedTime = _selectedTime.asStateFlow()

    fun onTimeSelected(time: Int) {
        _selectedTime.value = time
    }

    fun onConfirmClicked(location: Location) {
        viewModelScope.launch {
            _uiState.value = GenerateRouteUiState.Loading

            try {
                val response = hexagonRepository.generateRoute(
                    location.latitude,
                    location.longitude,
                    _selectedTime.value
                )

                if (response != null) {
                    _uiState.value = GenerateRouteUiState.Success(response)
                } else {
                    handleLogout()
                }
            } catch (e: Exception) {
                _uiState.value = GenerateRouteUiState.Error(e.message ?: "Unknown error.")
            }
        }
    }

    fun resetState() {
        _selectedTime.value = availableTimes.first()
        _uiState.value = GenerateRouteUiState.Choose
    }

    // Handles logout by clearing token
    private fun handleLogout() {
        viewModelScope.launch {
            tokenService.clearToken()

            _uiEvent.send(GenerateRouteUiEvent.NavigateToLogin)
        }
    }
}