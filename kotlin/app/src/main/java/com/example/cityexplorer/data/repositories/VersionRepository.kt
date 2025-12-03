package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.VersionApiService

class VersionRepository(private val apiService: VersionApiService) {
    suspend fun getCurrentVersion(key: String): String {
        return apiService.getCurrentVersion(key).string()
    }
}