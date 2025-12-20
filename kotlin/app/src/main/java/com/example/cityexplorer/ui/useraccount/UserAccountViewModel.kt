package com.example.cityexplorer.ui.useraccount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.Screen
import com.example.cityexplorer.data.dtos.GetUserStatisticsDto
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.TokenService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UserAccountUiState {
    data object Loading : UserAccountUiState
    data class Success(val data: GetUserStatisticsDto): UserAccountUiState
    data class Error(val message: String) : UserAccountUiState
}

interface UserAccountUiEvent {
    data object NavigateBack : UserAccountUiEvent
    data class ShowToast(val message: String) : UserAccountUiEvent
}

@HiltViewModel
class UserAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val tokenService: TokenService
) : ViewModel() {
    val city: String = savedStateHandle[Screen.Args.CITY] ?: ""

    var uiState: UserAccountUiState by mutableStateOf(UserAccountUiState.Loading)
        private set

    private val _uiEvent = Channel<UserAccountUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    init {
        loadData(false)
    }

    fun refreshData() {
        loadData(true)
    }

    private fun loadData(forceRefresh: Boolean) {
        if (!forceRefresh) uiState = UserAccountUiState.Loading else isRefreshing = true

        viewModelScope.launch {
           uiState = UserAccountUiState.Loading

            try {
                userRepository.getUserStatistics(city)
                    .onSuccess { response ->
                        uiState = UserAccountUiState.Success(response)
                    }
                    .onFailure {
                        uiState = UserAccountUiState.Error("Couldn't load data.")
                    }
            } catch (_: Exception) {
                uiState = UserAccountUiState.Error("Couldn't load data.")
            } finally {
                isRefreshing = false
            }
        }
    }

    fun handleLogout() {
        viewModelScope.launch {
            stopLocationService()

            tokenService.clearToken()

            _uiEvent.send(UserAccountUiEvent.ShowToast("Logged out."))

            _uiEvent.send(UserAccountUiEvent.NavigateBack)
        }
    }

    fun handleDeleteAccount() {
        viewModelScope.launch {
            stopLocationService()

            userRepository.deleteUserAccount()

            tokenService.clearToken()

            _uiEvent.send(UserAccountUiEvent.ShowToast("Account deleted."))

            _uiEvent.send(UserAccountUiEvent.NavigateBack)
        }
    }

    private fun stopLocationService() {
//        val intent = Intent(context, LocationTrackingService::class.java).apply {
//            action = LocationTrackingService.ACTION_STOP
//        }
//
//        context.startService(intent)
    }
}
