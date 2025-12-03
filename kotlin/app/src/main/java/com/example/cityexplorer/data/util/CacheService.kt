package com.example.cityexplorer.data.util

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import java.lang.reflect.Type

class CacheService(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun <T> saveToCache(key: String, version: String, data: T) {
        sharedPreferences.edit {
            putString(getVersionKey(key), version)
            putString(getDataKey(key), gson.toJson(data))
        }
    }

    fun getCachedVersion(key: String): String? {
        return sharedPreferences.getString(getVersionKey(key), null)
    }

    fun <T> getCachedData(key: String, type: Type): T? {
        val json = sharedPreferences.getString(getDataKey(key), null) ?: return null

        return try {
            gson.fromJson<T>(json, type)
        } catch (_: Exception) {
            null
        }
    }

    private fun getVersionKey(baseKey: String) = "$baseKey.version"
    private fun getDataKey(baseKey: String) = "$baseKey.data"

    companion object {
        private const val PREFS_NAME = "app_data_cache"
    }
}