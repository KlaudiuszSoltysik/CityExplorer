package com.example.cityexplorer.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.util.TokenManager
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data object Waiting : MainUiState
    data class Error(val message: String) : MainUiState
}

class LoginViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {
    var uiState: MainUiState by mutableStateOf(MainUiState.Waiting)
        private set

    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    fun resetState() {
        uiState = MainUiState.Waiting
    }

    fun onGoogleLoginSuccess(token: String, onNavigateNext: () -> Unit) {
        viewModelScope.launch {
            uiState = MainUiState.Loading

            tokenManager.saveToken(token)

            // Validate token in backend
            if (token.isNotEmpty()) {
                onNavigateNext()
            } else {
                uiState = MainUiState.Error("User not logged")
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


