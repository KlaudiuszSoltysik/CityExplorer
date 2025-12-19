package com.example.cityexplorer.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import kotlinx.coroutines.launch
import android.location.Location
import androidx.lifecycle.ViewModelProvider
import com.example.cityexplorer.data.dtos.HexagonProgressDto
import com.example.cityexplorer.data.dtos.HexagonsDto
import com.example.cityexplorer.data.dtos.SelectedHexagonDto
import com.example.cityexplorer.data.repositories.InvalidTokenException
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.ExplorationState
import com.example.cityexplorer.data.util.ServiceStateManager
import com.example.cityexplorer.data.util.TokenService
import com.example.cityexplorer.data.util.getLocationFlow
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

sealed interface MapUiState {
    data object Loading : MapUiState
    data class Success(val cityHexagonsDataDto: GetCityHexagonsDataDto) : MapUiState
    data class Error(val message: String) : MapUiState
}

interface MapUiEvent {
    data class ToggleService(val shouldStart: Boolean) : MapUiEvent
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
    val explorationState: ExplorationState = ExplorationState.STOPPED,
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
    val city: String,
    private val locationClient: FusedLocationProviderClient,
    private val tokenService: TokenService,
    private val hexagonRepository: HexagonRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val _uiEvent = Channel<MapUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()
    private val _state = MutableStateFlow(MapScreenState())
    val state = _state.asStateFlow()
    private var toggleJob: Job? = null
    private val currentHexagons: List<HexagonsDto>
        get() = (state.value.dataState as? MapUiState.Success)?.cityHexagonsDataDto?.hexagons ?: emptyList()

    // Initializes view model and starts location tracking if service is active
    init {
        viewModelScope.launch {
            ServiceStateManager.currentState
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
            _uiEvent.send(MapUiEvent.ToggleService(false))
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
        _state.update { it.copy(explorationState = ExplorationState.STOPPED) }
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

            val currentRealState = ServiceStateManager.currentState.value
            if (currentRealState != ExplorationState.STOPPED) {
                _uiEvent.send(MapUiEvent.ToggleService(false))
                return@launch
            }

            try {
                _state.update { it.copy(isExplorerButtonLoading = true) }
                _uiEvent.send(MapUiEvent.ToggleService(true))

                userRepository.getLoggedUser()
                    .onSuccess { response ->
                        if (!response.isAuthorized) {
                            _uiEvent.send(MapUiEvent.ToggleService(false))

                            handleLogout()
                        }
                    }
                    .onFailure {
                        _uiEvent.send(MapUiEvent.ToggleService(false))

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
        } catch (_: Exception) {
            _state.update { it.copy(dataState = MapUiState.Error("Couldn't load data. Check internet connection.")) }
        } finally {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    // Fetches hexagon progresses
    suspend fun loadProgressesData(city: String) {
        try {
            val progressList = hexagonRepository.getHexagonProgresses(city)

            applyProgressUpdatesToState(progressList)
        } catch (_: InvalidTokenException) {
            _uiEvent.send(MapUiEvent.ShowToast("Login to see progress."))
        } catch (_: Exception) {
            _uiEvent.send(MapUiEvent.ShowToast("Couldn't load data. Check internet connection."))
        }
    }

    // Identifies target hexagon (by ID or Location) and fetches its POIs
    fun getPoisFromHexagon(hexagonId: String?, hexagonWeight: Double?) {
        viewModelScope.launch {
            var targetHexId = hexagonId
            var targetWeight = hexagonWeight

            val currentState = state.value

            if (targetHexId == null && currentState.userLocation != null) {
                val userLatLng = LatLng(currentState.userLocation.latitude, currentState.userLocation.longitude)
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
                    _state.update {
                        it.copy(
                            selectedHexagonPois = SelectedHexagonDto(
                                weight = targetWeight,
                                pois = emptyList()
                            )
                        )
                    }

                    val pois = hexagonRepository.getPoisFromHexagon(targetHexId)

                    _state.update {
                        it.copy(
                            selectedHexagonPois = SelectedHexagonDto(
                                weight = targetWeight,
                                pois = pois
                            )
                        )
                    }
                } catch (_: Exception) {
                    _uiEvent.send(MapUiEvent.ShowToast("Failed to load POIs."))
                }
            }
        }
    }

    // Updates state with new progresses
    private fun applyProgressUpdatesToState(updates: List<HexagonProgressDto>) {
        val currentUiState = state.value.dataState

        if (currentUiState is MapUiState.Success) {
            val updatesMap = updates.associate { it.hexagonId to it.progress }
            val currentCityData = currentUiState.cityHexagonsDataDto

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
                        cityHexagonsDataDto = currentCityData.copy(
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
            } catch (_: SecurityException) {
                _state.update { it.copy(dataState = MapUiState.Error("Missing location permissions."))
                }
            } catch (_: Exception) {
                _state.update { it.copy(dataState = MapUiState.Error("Location error.")) }
            }
        }
    }

    // Checks wheater to navigate to login or user account screen
    fun onUserAccountButtonClick() {
        viewModelScope.launch {
            _state.update { it.copy(isUserAccountButtonLoading = true) }

            userRepository.getLoggedUser()
                .onSuccess { response ->
                    if (response.isAuthorized) {
                        _uiEvent.send(MapUiEvent.NavigateToUserAccount)
                    } else {
                        handleLogout()
                    }
                }
                .onFailure {
                    handleLogout()
                }

            _state.update { it.copy(isUserAccountButtonLoading = false) }
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
