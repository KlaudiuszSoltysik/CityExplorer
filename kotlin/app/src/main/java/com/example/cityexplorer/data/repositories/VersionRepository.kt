package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.VersionApiClient

class VersionRepository(private val apiService: VersionApiClient) {
    suspend fun getCurrentVersion(key: String): String {
        return apiService.getCurrentVersion(key).string()
    }
}