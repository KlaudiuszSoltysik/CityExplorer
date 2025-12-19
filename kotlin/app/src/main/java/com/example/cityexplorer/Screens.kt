package com.example.cityexplorer

sealed class Screen(val route: String) {
    data object LoginScreen : Screen("login?${Args.RETURN_ROUTE}={${Args.RETURN_ROUTE}}") {
        fun createRoute(returnRoute: String? = null): String {
            return if (returnRoute != null) {
                "login?${Args.RETURN_ROUTE}=$returnRoute"
            } else {
                "login"
            }
        }
    }

    data object CitySelectorScreen : Screen("city_selector")

    data object MapScreen : Screen("map/{${Args.CITY}}") {
        fun createRoute(city: String): String = "map/$city"
    }

    data object UserAccountScreen : Screen("user_account/{${Args.CITY}}") {
        fun createRoute(city: String): String = "user_account/$city"
    }

    object Args {
        const val CITY = "city"
        const val RETURN_ROUTE = "returnRoute"
    }
}