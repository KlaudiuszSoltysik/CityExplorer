package com.example.cityexplorer

import android.app.Application
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.CacheService
import com.example.cityexplorer.data.util.TokenService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CityExplorerApp : Application() {

    @Inject
    lateinit var cacheService: CacheService
    @Inject
    lateinit var tokenService: TokenService
    @Inject
    lateinit var hexagonRepository: HexagonRepository
    @Inject
    lateinit var userRepository: UserRepository
}