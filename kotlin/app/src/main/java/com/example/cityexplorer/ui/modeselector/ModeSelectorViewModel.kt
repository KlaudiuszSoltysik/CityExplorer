package com.example.cityexplorer.ui.map

import androidx.lifecycle.ViewModel

class MapViewModel(city: String) : ViewModel() {
    private val selectedCity = city
}

@Suppress("UNCHECKED_CAST")
class MapViewModelFactory(
    private val city: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(city) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}