package com.example.cityexplorer.data.repositories

import com.example.cityexplorer.data.api.VersionApiService

class VersionRepository(private val apiService: VersionApiService) {
    suspend fun getCurrentVersion(key: String): String {
        val response = apiService.getCurrentVersion(key)

        return response.string()
    }
}