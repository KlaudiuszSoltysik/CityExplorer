package com.example.cityexplorer.ui.useraccount

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.Screen
import com.example.cityexplorer.data.dtos.GetUserStatisticsResponseDto
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.LocationTrackingService
import com.example.cityexplorer.data.util.TokenService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UserAccountUiState {
    data object Loading : UserAccountUiState
    data class Success(val data: GetUserStatisticsResponseDto) : UserAccountUiState
    data class Error(val message: String) : UserAccountUiState
}

interface UserAccountUiEvent {
    data object NavigateBack : UserAccountUiEvent
}

@HiltViewModel
class UserAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val tokenService: TokenService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val city: String = savedStateHandle[Screen.Args.CITY] ?: ""

    private val _uiState = MutableStateFlow<UserAccountUiState>(UserAccountUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = Channel<UserAccountUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

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
                _uiState.value = UserAccountUiState.Loading
            } else {
                _isRefreshing.value = true
            }

            try {
                userRepository.getUserStatistics(city)
                    .onSuccess { response ->
                        _uiState.value = UserAccountUiState.Success(response)
                    }
                    .onFailure {
                        _uiState.value = UserAccountUiState.Error(it.message ?: "Unknown error.")
                    }
            } catch (e: Exception) {
                _uiState.value = UserAccountUiState.Error(e.message ?: "Unknown error.")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun handleLogout() {
        viewModelScope.launch {
            stopLocationService()

            tokenService.clearToken()

            _uiEvent.send(UserAccountUiEvent.NavigateBack)
        }
    }

    fun handleDeleteAccount() {
        viewModelScope.launch {
            stopLocationService()

            userRepository.deleteUserAccount()

            tokenService.clearToken()

            _uiEvent.send(UserAccountUiEvent.NavigateBack)
        }
    }

    private fun stopLocationService() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }

        context.startService(intent)
    }
}
