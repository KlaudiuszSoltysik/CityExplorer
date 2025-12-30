package com.example.cityexplorer.ui.map

import android.location.Location
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.Screen
import com.example.cityexplorer.data.dtos.GetHexagonsFromCityResponseDto
import com.example.cityexplorer.data.dtos.HexagonProgress
import com.example.cityexplorer.data.dtos.SelectedHexagon
import com.example.cityexplorer.data.dtos.WorkerResult
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.LocationTrackingService
import com.example.cityexplorer.data.util.TokenService
import com.example.cityexplorer.data.util.getLocationFlow
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MapUiState {
    data object Loading : MapUiState
    data class Success(val data: GetHexagonsFromCityResponseDto) : MapUiState
    data class Error(val message: String) : MapUiState
}

interface MapUiEvent {
    data class ToggleLocationTrackingService(val shouldStart: Boolean) : MapUiEvent
    data object NavigateToLogin : MapUiEvent
    data object NavigateToUserAccount : MapUiEvent
    data class ShowToast(val message: String) : MapUiEvent
    data object RequestPermissions : MapUiEvent
}

data class MapScreenState(
    val dataState: MapUiState = MapUiState.Loading,
    val isRefreshing: Boolean = false,
    val isExplorerButtonLoading: Boolean = false,
    val isUserAccountButtonLoading: Boolean = false,
    val userLocation: Location? = null,
    val explorationState: LocationTrackingService.ExplorationState = LocationTrackingService.ExplorationState.STOPPED,
    val arePermissionsGranted: Boolean = false,
    val selectedHexagonPois: SelectedHexagon? = null,
    val route: WorkerResult? = null
) {
    val isUserInCity: Boolean
        get() {
            val location = userLocation ?: return false
            val successState = dataState as? MapUiState.Success ?: return false
            val bbox = successState.data.bbox

            if (bbox.size < 4) {
                return false
            }

            return location.latitude in bbox[0]..bbox[2] &&
                    location.longitude in bbox[1]..bbox[3]
        }
}

@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationClient: FusedLocationProviderClient,
    private val tokenService: TokenService,
    private val hexagonRepository: HexagonRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    val city: String = savedStateHandle[Screen.Args.CITY] ?: ""

    private val _uiEvent = Channel<MapUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _state = MutableStateFlow(MapScreenState())
    val state = _state.asStateFlow()

    private var toggleJob: Job? = null

    // Initializes view model and starts location tracking if service is active
    init {
        viewModelScope.launch {
            tokenService.tokenState.collect { token ->
                if (token == null) {
                    clearProgress()
                }
            }
        }

        viewModelScope.launch {
            LocationTrackingService.ServiceStateManager.currentState
                .collect { realServiceState ->
                    _state.update {
                        it.copy(
                            explorationState = realServiceState,
                            isExplorerButtonLoading = false
                        )
                    }
                }
        }

        viewModelScope.launch {
            loadHexagonData(city, true)
            loadProgressesData(city)
        }

        viewModelScope.launch {
            hexagonRepository.hexagonUpdates.collect { updates ->
                applyProgressUpdatesToState(updates)
            }
        }
    }

    // Stops location tracking service on view model clear
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            _uiEvent.send(MapUiEvent.ToggleLocationTrackingService(false))
        }
    }

    // Refreshes data from the server
    fun refreshData() {
        viewModelScope.launch {
            loadHexagonData(city, false)
            loadProgressesData(city)
        }
    }

    // Show toast if user doesn't grant location permissions
    fun showPermissionToast() {
        viewModelScope.launch {
            _uiEvent.send(MapUiEvent.ShowToast("App needs your precise location."))
        }
    }

    // Updates permission status and starts location tracking if granted
    fun updatePermissionStatus(isGranted: Boolean) {
        _state.update { it.copy(arePermissionsGranted = isGranted) }

        if (isGranted) {
            startLocationTracking()
        }
    }

    // Stop location tracking service
    fun onServiceStoppedExternal() {
        _state.update { it.copy(explorationState = LocationTrackingService.ExplorationState.STOPPED) }
    }

    // Toggles the exploration mode after validating requirements
    fun onExplorerToggleClick() {
        toggleJob?.cancel()

        toggleJob = viewModelScope.launch {
            val currentState = state.value

            if (currentState.isExplorerButtonLoading) return@launch

            if (!currentState.arePermissionsGranted) {
                _uiEvent.send(MapUiEvent.RequestPermissions)
                return@launch
            }
            if (!currentState.isUserInCity) {
                _uiEvent.send(MapUiEvent.ShowToast("You are not in the city!"))
                return@launch
            }

            val currentRealState = LocationTrackingService.ServiceStateManager.currentState.value
            if (currentRealState != LocationTrackingService.ExplorationState.STOPPED) {
                _uiEvent.send(MapUiEvent.ToggleLocationTrackingService(false))
                return@launch
            }

            try {
                _state.update { it.copy(isExplorerButtonLoading = true) }
                _uiEvent.send(MapUiEvent.ToggleLocationTrackingService(true))

                userRepository.getLoggedUser()
                    .onFailure {
                        _uiEvent.send(MapUiEvent.ToggleLocationTrackingService(false))

                        handleLogout()
                    }
            } finally {
                _state.update { it.copy(isExplorerButtonLoading = false) }
            }
        }
    }

    // Fetches hexagon data with repo-managed fallback logic
    suspend fun loadHexagonData(city: String, isInitial: Boolean) {
        if (isInitial) {
            _state.update { it.copy(dataState = MapUiState.Loading) }
        } else {
            _state.update { it.copy(isRefreshing = true) }
        }

        try {
            val result = hexagonRepository.getHexagonsFromCity(city, forceRefresh = !isInitial)

            val data = when (result) {
                is HexagonRepository.GetHexagonsFromCityResult.Success -> result.data
                is HexagonRepository.GetHexagonsFromCityResult.Fallback -> {
                    _uiEvent.send(MapUiEvent.ShowToast("Offline mode!"))
                    result.data
                }
            }

            _state.update { it.copy(dataState = MapUiState.Success(data)) }
        } catch (e: Exception) {
            _state.update { it.copy(dataState = MapUiState.Error(e.message ?: "Unknown error.")) }
        } finally {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    // Fetches hexagon progresses
    suspend fun loadProgressesData(city: String) {
        try {
            val progressList = hexagonRepository.getHexagonProgresses(city)

            applyProgressUpdatesToState(progressList)
        } catch (_: Exception) {
            _uiEvent.send(MapUiEvent.ShowToast("Login to see progress."))
        }
    }

    // Identifies target hexagon (by ID or Location) and fetches its POIs
    fun getPoisFromHexagon(hexagonId: String?, hexagonWeight: Double?) {
        viewModelScope.launch {
            if (hexagonId == null || hexagonWeight == null) {
                _state.update { it.copy(selectedHexagonPois = null) }
            } else {
                try {
                    _state.update {
                        it.copy(
                            selectedHexagonPois = SelectedHexagon(
                                weight = hexagonWeight,
                                pois = emptyList()
                            )
                        )
                    }

                    val pois = hexagonRepository.getPoisFromHexagon(hexagonId)

                    _state.update {
                        it.copy(
                            selectedHexagonPois = SelectedHexagon(
                                weight = hexagonWeight,
                                pois = pois
                            )
                        )
                    }
                } catch (e: Exception) {
                    _uiEvent.send(MapUiEvent.ShowToast(e.message ?: "Unknown error."))
                }
            }
        }
    }

    // Updates state with new progresses
    private fun applyProgressUpdatesToState(updates: List<HexagonProgress>) {
        val currentUiState = state.value.dataState

        if (currentUiState is MapUiState.Success) {
            val updatesMap = updates.associate { it.hexagonId to it.progress }
            val currentCityData = currentUiState.data

            val updatedHexagons = currentCityData.hexagons.map { hexagon ->
                if (updatesMap.containsKey(hexagon.id)) {
                    hexagon.copy(progress = updatesMap[hexagon.id]!!)
                } else {
                    hexagon
                }
            }

            _state.update {
                it.copy(
                    dataState = MapUiState.Success(
                        data = currentCityData.copy(
                            hexagons = updatedHexagons
                        )
                    )
                )
            }
        }
    }

    // Starts observing location updates flow
    private fun startLocationTracking() {
        viewModelScope.launch {
            try {
                locationClient.getLocationFlow()
                    .collect { location ->
                        _state.update { it.copy(userLocation = location) }
                    }
            } catch (e: SecurityException) {
                _state.update {
                    it.copy(
                        dataState = MapUiState.Error(
                            e.message ?: "Unknown error."
                        )
                    )
                }
            }
        }
    }

    // Checks wheater to navigate to login or user account screen
    fun onUserAccountButtonClick() {
        viewModelScope.launch {
            _state.update { it.copy(isUserAccountButtonLoading = true) }

            userRepository.getLoggedUser()
                .onSuccess {
                    _uiEvent.send(MapUiEvent.NavigateToUserAccount)
                }
                .onFailure {
                    handleLogout()
                }

            _state.update { it.copy(isUserAccountButtonLoading = false) }
        }
    }

    // Handles new route from server by saving it to state
    fun handleNewRoute(route: WorkerResult) {
        _state.update { it.copy(route = route) }
    }

    // Clears route from state
    fun clearRoute() {
        _state.update { it.copy(route = null) }
    }

    // Clears user progress (after logging out)
    fun clearProgress() {
        val currentUiState = state.value.dataState

        if (currentUiState is MapUiState.Success) {
            val currentCityData = currentUiState.data

            val updatedHexagons = currentCityData.hexagons.map { hexagon ->
                hexagon.copy(progress = 0.0)
            }

            _state.update {
                it.copy(
                    dataState = MapUiState.Success(
                        data = currentCityData.copy(
                            hexagons = updatedHexagons
                        )
                    )
                )
            }
        }
    }

    // Handles logout by clearing token
    private fun handleLogout() {
        viewModelScope.launch {
            tokenService.clearToken()

            _uiEvent.send(MapUiEvent.NavigateToLogin)
        }
    }
}
