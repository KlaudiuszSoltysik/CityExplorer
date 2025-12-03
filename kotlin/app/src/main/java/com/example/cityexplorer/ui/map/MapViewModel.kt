package com.example.cityexplorer.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import kotlinx.coroutines.launch
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.example.cityexplorer.data.dtos.HexagonsDto
import com.example.cityexplorer.data.dtos.SelectedHexagonDto
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.TokenService
import com.example.cityexplorer.data.util.getLocationFlow
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

sealed interface MapUiState {
    data object Loading : MapUiState
    data class Success(val cityHexagonsDataDto: GetCityHexagonsDataDto) : MapUiState
    data class Error(val message: String) : MapUiState
}

interface MapUiEvent {
    data class ToggleService(val shouldStart: Boolean) : MapUiEvent
    data object NavigateToLogin : MapUiEvent
    data class ShowError(val message: String) : MapUiEvent
    data object RequestPermissions : MapUiEvent
}

data class MapScreenState(
    val dataState: MapUiState = MapUiState.Loading,
    val isRefreshing: Boolean = false,
    val userLocation: Location? = null,
    val isExploringMode: Boolean = false,
    val arePermissionsGranted: Boolean = false,
    val selectedHexagonPois: SelectedHexagonDto = SelectedHexagonDto()
) {
    val isUserInCity: Boolean
        get() {
            val location = userLocation ?: return false
            val successState = dataState as? MapUiState.Success ?: return false
            val bbox = successState.cityHexagonsDataDto.bbox

            if (bbox.size < 4) {
                return false
            }

            return location.latitude in bbox[0]..bbox[2] &&
                    location.longitude in bbox[1]..bbox[3]
        }
}

class MapViewModel(
    private val city: String,
    private val locationClient: FusedLocationProviderClient,
    private val tokenService: TokenService,
    private val hexagonRepository: HexagonRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val _uiEvent = Channel<MapUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    var state by mutableStateOf(MapScreenState())
        private set

    private val currentHexagons: List<HexagonsDto>
        get() = (state.dataState as? MapUiState.Success)?.cityHexagonsDataDto?.hexagons ?: emptyList()

    init {
        loadData(city, isInitial = true)
    }

    fun refreshData() {
        loadData(city, isInitial = false)
    }

    fun updatePermissionStatus(isGranted: Boolean) {
        state = state.copy(arePermissionsGranted = isGranted)
        if (isGranted) {
            startLocationTracking()
        }
    }

    fun onServiceStoppedExternal() {
        state = state.copy(isExploringMode = false)
    }

    // Toggles the exploration mode after validating requirements
    fun onExplorerToggleClick() {
        viewModelScope.launch {
            if (!state.arePermissionsGranted) {
                _uiEvent.send(MapUiEvent.RequestPermissions)
                return@launch
            }

            if (!state.isUserInCity) {
                _uiEvent.send(MapUiEvent.ShowError("You are not in the city!"))
                return@launch
            }

            val token = tokenService.getToken()
            if (token == null) {
                _uiEvent.send(MapUiEvent.NavigateToLogin)
                return@launch
            }

            val newMode = !state.isExploringMode
            state = state.copy(isExploringMode = newMode)

            _uiEvent.send(MapUiEvent.ToggleService(newMode))

            try {
                val userResponse = userRepository.getLoggedUser(token)

                if (!userResponse.isAuthorized) {
                    state = state.copy(isExploringMode = !newMode)
                    handleLogout()
                }

            } catch (_: Exception) {
                state = state.copy(isExploringMode = !newMode)
                _uiEvent.send(MapUiEvent.ToggleService(!newMode))
                _uiEvent.send(MapUiEvent.ShowError("Server error"))
            }
        }
    }

    // Fetches hexagon data with repo-managed fallback logic
    fun loadData(city: String, isInitial: Boolean) {
        viewModelScope.launch {
            state = if (isInitial) {
                state.copy(dataState = MapUiState.Loading)
            } else {
                state.copy(isRefreshing = true)
            }

            try {
                val result = hexagonRepository.getHexagonsFromCity(city, forceRefresh = !isInitial)

                val data = when (result) {
                    is HexagonRepository.RepoResult.Success -> result.data
                    is HexagonRepository.RepoResult.Fallback -> {
                        _uiEvent.send(MapUiEvent.ShowError("Offline mode!"))
                        result.data
                    }
                }

                state = state.copy(
                    dataState = MapUiState.Success(data)
                )

            } catch (e: Exception) {
                e.message?.let { Log.e("error", it) }
                state = state.copy(
                    dataState = MapUiState.Error("Couldn't load data. Check internet connection.")
                )
            } finally {
                state = state.copy(isRefreshing = false)
            }
        }
    }

    // Identifies target hexagon (by ID or Location) and fetches its POIs
    fun getPoisFromHexagon(hexagonId: String?, hexagonWeight: Double?) {
        viewModelScope.launch {
            var targetHexId = hexagonId
            var targetWeight = hexagonWeight

            if (targetHexId == null && state.userLocation != null) {
                val userLatLng = LatLng(state.userLocation!!.latitude, state.userLocation!!.longitude)
                val hexList = currentHexagons

                val foundHexagon = withContext(Dispatchers.Default) {
                    hexList.find { hex ->
                        val polygonPath = hex.boundaries.map { point -> LatLng(point[0], point[1]) }
                        PolyUtil.containsLocation(userLatLng, polygonPath, true)
                    }
                }

                targetHexId = foundHexagon?.id
                targetWeight = foundHexagon?.weight
            }

            if (targetHexId != null && targetWeight != null) {
                try {
                    val pois = hexagonRepository.getPoisFromHexagon(targetHexId)

                    state = state.copy(selectedHexagonPois = SelectedHexagonDto(
                        weight = targetWeight,
                        pois = pois
                    ))
                } catch (_: Exception) {
                    _uiEvent.send(MapUiEvent.ShowError("Failed to load POIs."))
                }
            }
        }
    }

    // Starts observing location updates flow
    private fun startLocationTracking() {
        viewModelScope.launch {
            try {
                locationClient.getLocationFlow()
                    .collect { location ->
                        state = state.copy(userLocation = location)
                    }
            } catch (_: SecurityException) {
                state = state.copy(dataState = MapUiState.Error("Missing location permissions."))
            } catch (_: Exception) {
                state = state.copy(dataState = MapUiState.Error("Location error"))
            }
        }
    }

    private suspend fun handleLogout() {
        tokenService.clearToken()
        _uiEvent.send(MapUiEvent.NavigateToLogin)
    }
}

@Suppress("UNCHECKED_CAST")
class MapViewModelFactory(
    private val city: String,
    private val locationClient: FusedLocationProviderClient,
    private val tokenService: TokenService,
    private val hexagonRepository: HexagonRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(
                city,
                locationClient,
                tokenService,
                hexagonRepository,
                userRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
