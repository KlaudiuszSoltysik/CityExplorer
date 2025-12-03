package com.example.cityexplorer.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityexplorer.data.api.ApiClient
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import kotlinx.coroutines.launch
import android.location.Location
import android.util.Log
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.dtos.HexagonsDto
import com.example.cityexplorer.data.dtos.SelectedHexagonDto
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.repositories.VersionRepository
import com.example.cityexplorer.data.util.CacheService
import com.example.cityexplorer.data.util.TokenService
import com.example.cityexplorer.data.util.getLocationFlow
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import com.google.gson.reflect.TypeToken
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(val cityHexagonsDataDto: GetCityHexagonsDataDto) : MainUiState
    data class Error(val message: String) : MainUiState
}

interface MapUiEvent {
    data class ToggleService(val shouldStart: Boolean) : MapUiEvent
    data object NavigateToLogin : MapUiEvent
    data class ShowError(val message: String) : MapUiEvent
    data object RequestPermissions : MapUiEvent
}

class MapViewModel(
    private val city: String,
    private val locationClient: FusedLocationProviderClient,
    private val tokenService: TokenService,
    private val cacheService: CacheService
) : ViewModel() {
    private val hexagonRepository = HexagonRepository(ApiClient.hexagonApiService)
    private val versionRepository = VersionRepository(ApiClient.versionApiService)
    private val userRepository = UserRepository(ApiClient.userApiService)
    private val _uiEvent = Channel<MapUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()
    var uiState: MainUiState by mutableStateOf(MainUiState.Loading)
        private set
    var isRefreshing: Boolean by mutableStateOf(false)
        private set
    var userLocation: Location? by mutableStateOf(null)
        private set
    var isExploringMode: Boolean by mutableStateOf(false)
        private set
    var arePermissionsGranted: Boolean by mutableStateOf(false)
        private set
    var hexagonsList: List<HexagonsDto> by mutableStateOf(emptyList())
        private set
    var hexagonPois: SelectedHexagonDto by mutableStateOf(SelectedHexagonDto())
        private set
    val isUserInCity: Boolean
        get() {
            val location = userLocation ?: return false
            val state = uiState as? MainUiState.Success ?: return false
            val bbox = state.cityHexagonsDataDto.bbox

            return location.latitude in bbox[0]..bbox[2] &&
                    location.longitude in bbox[1]..bbox[3]
        }

    init {
        loadData(isInitial = true)
    }

    fun refreshData() {
        loadData(isInitial = false)
    }

    fun updatePermissionStatus(isGranted: Boolean) {
        arePermissionsGranted = isGranted
        if (isGranted) {
            startLocationTracking()
        }
    }

    fun onExplorerToggleClick() {
        viewModelScope.launch {
            if (!arePermissionsGranted) {
                _uiEvent.send(MapUiEvent.RequestPermissions)
                return@launch
            }

            if (!isUserInCity) {
                _uiEvent.send(MapUiEvent.ShowError("You are not in the city!"))
                return@launch
            }

            val token = tokenService.getToken()

            if (token == null) {
                _uiEvent.send(MapUiEvent.NavigateToLogin)
                return@launch
            }

            try {
                val getUserResponseDto = userRepository.getLoggedUser(token)

                if (getUserResponseDto.isAuthorized) {
                    isExploringMode = !isExploringMode

                    _uiEvent.send(MapUiEvent.ToggleService(isExploringMode))
                } else {
                    handleLogout()
                }
            } catch (_: Exception) {
                _uiEvent.send(MapUiEvent.ShowError("Server error."))
            }
        }
    }

    suspend fun handleLogout() {
        tokenService.clearToken()
        _uiEvent.send(MapUiEvent.NavigateToLogin)
    }

    fun onServiceStoppedExternal() {
        isExploringMode = false
    }

    fun loadData(isInitial: Boolean) {
        viewModelScope.launch {
            val key = "get-hexagons-from-city-$city"

            if (isInitial) uiState = MainUiState.Loading else isRefreshing = true

            try {
                val remoteVersion = versionRepository.getCurrentVersion(key)
                val cachedVersion = cacheService.getCachedVersion(key)

                val dtoType = object : TypeToken<GetCityHexagonsDataDto>() {}.type

                val cachedData = if (cachedVersion == remoteVersion) {
                    cacheService.getCachedData<GetCityHexagonsDataDto>(key, dtoType)
                } else {
                    null
                }

                val data = if (cachedData != null) {
                    cachedData
                } else {
                    val networkData = hexagonRepository.getHexagonsFromCity(city)

                    cacheService.saveToCache(key, remoteVersion, networkData)

                    networkData
                }

                uiState = MainUiState.Success(data)
                hexagonsList = data.hexagons
            } catch (e: Exception) {
                e.message?.let { Log.e("MapViewModel", it) }
                if (isInitial) uiState = MainUiState.Error("Couldn't load data.")
            } finally {
                isRefreshing = false
            }
        }
    }

    fun getPoisFromHexagon(hexagonId: String?, hexagonWeight: Double?) {
        viewModelScope.launch {
            var targetHexagonId = hexagonId
            var targetHexagonWeight = hexagonWeight

            if (targetHexagonId == null && userLocation != null) {
                val userLatLng = LatLng(userLocation!!.latitude, userLocation!!.longitude)

                val foundHexagon = hexagonsList.find { hex ->
                    val rawBoundaries = hex.boundaries

                    val polygonPath = rawBoundaries.map { point ->
                        LatLng(point[0], point[1])
                    }

                    PolyUtil.containsLocation(userLatLng, polygonPath, true)
                }

                targetHexagonId = foundHexagon?.id
                targetHexagonWeight = foundHexagon?.weight
            }

            if (targetHexagonId != null && targetHexagonWeight != null) {
                try {
                    val result = hexagonRepository.getPoisFromHexagon(targetHexagonId)

                    hexagonPois = SelectedHexagonDto (
                        weight = targetHexagonWeight,
                        pois = result
                    )
                } catch (e: Exception) {
                    println("Error fetching POIs: ${e.message}")
                }
            }
        }
    }

    fun startLocationTracking() {
        viewModelScope.launch {
            try {
                getLocationFlow(locationClient).collect { location ->
                    userLocation = location
                }
            } catch (_: Exception) {
                uiState = MainUiState.Error("App error.")
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class MapViewModelFactory(
    private val city: String,
    private val locationClient: FusedLocationProviderClient,
    private val tokenService: TokenService,
    private val cacheService: CacheService
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(city, locationClient, tokenService, cacheService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}