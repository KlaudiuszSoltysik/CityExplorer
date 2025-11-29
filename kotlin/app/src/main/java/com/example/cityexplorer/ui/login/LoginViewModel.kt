package com.example.cityexplorer.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.api.ApiClient
import com.example.cityexplorer.data.dtos.LoginRequestDto
import com.example.cityexplorer.data.util.TokenManager
import com.example.cityexplorer.data.repositories.UserRepository
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data object Waiting : MainUiState
    data class Error(val message: String) : MainUiState
}

class LoginViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {
    private val repository = UserRepository(ApiClient.userApiService)
    var uiState: MainUiState by mutableStateOf(MainUiState.Waiting)

    fun resetState() {
        uiState = MainUiState.Waiting
    }

    fun onGoogleLoginSuccess(token: String, onNavigateNext: () -> Unit) {
        viewModelScope.launch {
            uiState = MainUiState.Loading

            try {
                val loginRequestDto = LoginRequestDto(token = token)
                val loginResponseDto = repository.validateLoginToken(loginRequestDto)

                if (loginResponseDto.isSuccess && loginResponseDto.token != null) {
                    tokenManager.saveToken(loginResponseDto.token)

                    onNavigateNext()
                } else {
                    uiState = MainUiState.Error("Login failed on server")
                }
            } catch (e: Exception) {
                uiState = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

class LoginViewModelFactory(private val tokenManager: TokenManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(tokenManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


