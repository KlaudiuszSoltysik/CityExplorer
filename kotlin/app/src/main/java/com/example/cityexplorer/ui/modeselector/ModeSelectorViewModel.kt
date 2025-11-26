package com.example.cityexplorer.ui.modeselector

import androidx.lifecycle.ViewModel

class ModeSelectorViewModel(city: String) : ViewModel() {
    private val selectedCity = city
}

@Suppress("UNCHECKED_CAST")
class ModeSelectorViewModelFactory(
    private val city: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModeSelectorViewModel::class.java)) {
            return ModeSelectorViewModel(city) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}