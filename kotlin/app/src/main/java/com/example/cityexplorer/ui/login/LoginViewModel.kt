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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL

sealed interface MainUiState {
    data object Loading : MainUiState
    data object Waiting : MainUiState
}

interface LoginUiEvent {
    data class ShowError(val message: String) : LoginUiEvent
}

class LoginViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {
    private val repository = UserRepository(ApiClient.userApiService)
    private val _uiEvent = Channel<LoginUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()
    private val WEB_CLIENT_ID = "357422343630-2v64co21drksl119p77bjhs642qk3cmd.apps.googleusercontent.com"

    var uiState: MainUiState by mutableStateOf(MainUiState.Waiting)
        private set

    fun signInWithGoogle(context: android.content.Context, onNavigateNext: () -> Unit) {
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(WEB_CLIENT_ID)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context,
                )

                handleCredentialResult(result.credential, onNavigateNext)

            } catch (_: GetCredentialCancellationException) {
            } catch (_: GetCredentialException) {
                _uiEvent.send(LoginUiEvent.ShowError("Google login failed"))
            } catch (_: Exception) {
                _uiEvent.send(LoginUiEvent.ShowError("Login failed"))
            }
        }
    }

    private fun handleCredentialResult(credential: androidx.credentials.Credential, onNavigateNext: () -> Unit) {
        when (credential) {
            is GoogleIdTokenCredential -> {
                onGoogleLoginSuccess(credential.idToken, onNavigateNext)
            }

            is CustomCredential -> {
                if (credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        onGoogleLoginSuccess(googleIdTokenCredential.idToken, onNavigateNext)
                    } catch (_: Exception) {
                        viewModelScope.launch {
                            _uiEvent.send(LoginUiEvent.ShowError("Google login failed"))
                        }
                    }
                } else {
                    viewModelScope.launch {
                        _uiEvent.send(LoginUiEvent.ShowError("Google login failed"))
                    }
                }
            }
            else -> {
                viewModelScope.launch {
                    _uiEvent.send(LoginUiEvent.ShowError("Google login failed"))
                }
            }
        }
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
                    _uiEvent.send(LoginUiEvent.ShowError("Login failed on server"))
                }
            } catch (_: Exception) {
                _uiEvent.send(LoginUiEvent.ShowError("Login failed inside app"))
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


