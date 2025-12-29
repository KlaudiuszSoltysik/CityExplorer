package com.example.cityexplorer.data.util

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheService @Inject constructor(
    @ApplicationContext context: Context
) {
    @PublishedApi
    internal val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    inline fun <reified T> saveToCache(key: String, version: String, data: T) {
        val jsonString = json.encodeToString(data)

        sharedPreferences.edit {
            putString(getVersionKey(key), version)
            putString(getDataKey(key), jsonString)
        }
    }

    fun getCachedVersion(key: String): String? {
        return sharedPreferences.getString(getVersionKey(key), null)
    }

    inline fun <reified T> getCachedData(key: String): T? {
        val jsonString = sharedPreferences.getString(getDataKey(key), null) ?: return null

        return try {
            json.decodeFromString<T>(jsonString)
        } catch (_: Exception) {
            null
        }
    }

    @PublishedApi
    internal fun getVersionKey(baseKey: String) = "$baseKey.version"

    @PublishedApi
    internal fun getDataKey(baseKey: String) = "$baseKey.data"

    companion object {
        private const val PREFS_NAME = "app_data_cache"
    }
}
