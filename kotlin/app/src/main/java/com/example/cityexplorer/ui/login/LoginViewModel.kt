package com.example.cityexplorer.ui.login

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.util.TokenService
import com.example.cityexplorer.data.repositories.UserRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException
import com.example.cityexplorer.data.util.Constants
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed interface LoginUiState {
    data object Loading : LoginUiState
    data object Waiting : LoginUiState
}

interface LoginUiEvent {
    data class ShowError(val message: String) : LoginUiEvent
}

class LoginViewModel(
    private val tokenService: TokenService,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiEvent = Channel<LoginUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    var uiState: LoginUiState by mutableStateOf(LoginUiState.Waiting)
        private set

    fun onSignInClick(context: Context, onNavigateNext: () -> Unit) {
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(Constants.WEB_CLIENT_ID)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                handleCredentialResult(result.credential, onNavigateNext)

            } catch (_: GetCredentialException) {
                _uiEvent.send(LoginUiEvent.ShowError("Google credential error"))
            } catch (_: Exception) {
                _uiEvent.send(LoginUiEvent.ShowError("Login process failed"))
            }
        }
    }

    private fun handleCredentialResult(credential: androidx.credentials.Credential, onNavigateNext: () -> Unit) {
        when (credential) {
            is GoogleIdTokenCredential -> {
                authenticateWithBackend(credential.idToken, onNavigateNext)
            }
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        authenticateWithBackend(googleIdTokenCredential.idToken, onNavigateNext)
                    } catch (_: Exception) {
                        sendError("Invalid custom credential data")
                    }
                } else {
                    sendError("Unsupported credential type")
                }
            }
            else -> {
                sendError("Unknown credential type")
            }
        }
    }

    private fun authenticateWithBackend(token: String, onNavigateNext: () -> Unit) {
        viewModelScope.launch {
            uiState = LoginUiState.Loading

            try {
                val response = userRepository.validateLoginToken(token)

                if (response.isSuccess && response.token != null) {
                    tokenService.saveToken(response.token)
                    onNavigateNext()
                } else {
                    uiState = LoginUiState.Waiting
                    _uiEvent.send(LoginUiEvent.ShowError("Server validation failed"))
                }
            } catch (e: Exception) {
                uiState = LoginUiState.Waiting
                _uiEvent.send(LoginUiEvent.ShowError("Network error: ${e.message}"))
            }
        }
    }

    private fun sendError(message: String) {
        viewModelScope.launch {
            _uiEvent.send(LoginUiEvent.ShowError(message))
        }
    }
}

class LoginViewModelFactory(
    private val tokenService: TokenService,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(tokenService, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
