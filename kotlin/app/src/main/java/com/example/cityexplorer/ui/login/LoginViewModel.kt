package com.example.cityexplorer.ui.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.BuildConfig.WEB_CLIENT_ID
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.TokenService
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Loading : LoginUiState
    data object Waiting : LoginUiState
}

interface LoginUiEvent {
    data class ShowToast(val message: String) : LoginUiEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val tokenService: TokenService,
    private val userRepository: UserRepository
) : ViewModel() {
    private val _uiEvent = Channel<LoginUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Waiting)
    val uiState = _uiState.asStateFlow()

    fun onSignInClick(context: Context, onNavigateNext: () -> Unit) {
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

                val result = credentialManager.getCredential(context, request)
                handleCredentialResult(result.credential, onNavigateNext)

            } catch (_: GetCredentialException) {
                _uiEvent.send(LoginUiEvent.ShowToast("Google credential error."))
            } catch (_: Exception) {
                _uiEvent.send(LoginUiEvent.ShowToast("Login process failed."))
            }
        }
    }

    private fun handleCredentialResult(
        credential: androidx.credentials.Credential,
        onNavigateNext: () -> Unit
    ) {
        when (credential) {
            is GoogleIdTokenCredential -> {
                authenticateWithBackend(credential.idToken, onNavigateNext)
            }

            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential =
                            GoogleIdTokenCredential.createFrom(credential.data)
                        authenticateWithBackend(googleIdTokenCredential.idToken, onNavigateNext)
                    } catch (_: Exception) {
                        sendError("Invalid custom credential data.")
                    }
                } else {
                    sendError("Unsupported credential type.")
                }
            }

            else -> {
                sendError("Unknown credential type.")
            }
        }
    }

    private fun authenticateWithBackend(token: String, onNavigateNext: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            try {
                val response = userRepository.validateLoginToken(token)

                response
                    .onSuccess {
                        it.token.let { token ->
                            tokenService.saveToken(token)
                        }

                        onNavigateNext()
                    }
                    .onFailure {
                        _uiState.value = LoginUiState.Waiting
                        _uiEvent.send(LoginUiEvent.ShowToast(it.message ?: "Unknown error."))
                    }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Waiting
                _uiEvent.send(LoginUiEvent.ShowToast(e.message ?: "Unknown error."))
            }
        }
    }

    private fun sendError(message: String) {
        viewModelScope.launch {
            _uiEvent.send(LoginUiEvent.ShowToast(message))
        }
    }
}
