package com.example.cityexplorer

import android.app.Application
import com.example.cityexplorer.data.api.ApiClient
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.CacheService
import com.example.cityexplorer.data.util.TokenService

class CityExplorerApp : Application() {
    lateinit var tokenService: TokenService
        private set
    lateinit var hexagonRepository: HexagonRepository
        private set
    lateinit var userRepository: UserRepository
        private set

    override fun onCreate() {
        super.onCreate()

        tokenService = TokenService(applicationContext)
        val cacheService = CacheService(applicationContext)

        hexagonRepository = HexagonRepository(
            ApiClient.hexagonApiClient,
            ApiClient.versionApiClient,
            cacheService
        )

        userRepository = UserRepository(ApiClient.userApiClient)
    }
}