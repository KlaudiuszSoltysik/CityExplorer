package com.example.cityexplorer.ui.navigation

sealed class Screen(val route: String) {
    data object CitySelector : Screen("city_selector")
}