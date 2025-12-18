package com.example.cityexplorer.ui.useraccount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.dtos.GetUserResponseDto
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.TokenService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface UserAccountUiState {
    data object Loading : UserAccountUiState
    data class Success(val data: GetUserResponseDto): UserAccountUiState
    data class Error(val message: String) : UserAccountUiState
}

interface UserAccountUiEvent {
    data object NavigateBack : UserAccountUiEvent
}

class UserAccountViewModel(
    private val userRepository: UserRepository,
    private val tokenService: TokenService
) : ViewModel() {
    var uiState: UserAccountUiState by mutableStateOf(UserAccountUiState.Loading)
        private set

    private val _uiEvent = Channel<UserAccountUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
           uiState = UserAccountUiState.Loading

            try {
                val data = userRepository.getLoggedUser("")
                uiState = UserAccountUiState.Success(data)
            } catch (_: Exception) {
                uiState = UserAccountUiState.Error("Couldn't load data")
            }
        }
    }

    fun handleLogout() {
        viewModelScope.launch {
            tokenService.clearToken()

            _uiEvent.send(UserAccountUiEvent.NavigateBack)
        }
    }

    fun handleDeleteAccount() {
        viewModelScope.launch {
            tokenService.clearToken()

            _uiEvent.send(UserAccountUiEvent.NavigateBack)
        }
    }
}

class UserAccountViewModelFactory(private val userRepository: UserRepository, private val tokenService: TokenService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserAccountViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserAccountViewModel(userRepository, tokenService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}