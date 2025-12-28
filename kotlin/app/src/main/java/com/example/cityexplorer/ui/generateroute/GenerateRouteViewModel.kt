package com.example.cityexplorer.ui.generateroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.dtos.WorkerResultDto
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
    data class Success(val routeDto: WorkerResultDto) : GenerateRouteUiState
    data class Error(val message: String) : GenerateRouteUiState
}

@HiltViewModel
class GenerateRouteViewModel @Inject constructor(
    private val tokenService: TokenService,
    private val hexagonRepository: HexagonRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    val availableTimes = (30..300 step 30).toList()

    private val _selectedTime = MutableStateFlow(availableTimes.first())
    val selectedTime = _selectedTime.asStateFlow()

    private val _uiState = MutableStateFlow<GenerateRouteUiState>(GenerateRouteUiState.Choose)
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = Channel<GenerateRouteUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    fun onTimeSelected(time: Int) {
        _selectedTime.value = time
    }

    fun onConfirmClicked() {
        viewModelScope.launch {
            _uiState.value = GenerateRouteUiState.Loading

            try {
                userRepository.getLoggedUser()
                    .onSuccess { response ->
                        if (!response.isAuthorized) {
                            handleLogout()
                        }
                    }
                    .onFailure {
                        handleLogout()
                    }

                val response = hexagonRepository.generateRoute("891e24aaccbffff", _selectedTime.value)

                _uiState.value = GenerateRouteUiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = GenerateRouteUiState.Error(e.message ?: "Unknown error")
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