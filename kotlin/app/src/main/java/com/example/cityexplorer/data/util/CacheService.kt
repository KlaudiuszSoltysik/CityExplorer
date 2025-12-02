package com.example.cityexplorer.data.util

import android.content.Context
import com.google.gson.Gson
import java.lang.reflect.Type

class CacheService(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("app_data_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun <T> saveToCache(key: String, version: String, data: T) {
        sharedPreferences.edit().apply {
            putString("$key.version", version)
            putString("$key.data", gson.toJson(data))
            apply()
        }
    }

    fun getCachedVersion(key: String): String? {
        val version = sharedPreferences.getString("$key.version", null)
        return version
    }

    fun <T> getCachedData(key: String, type: Type): T? {
        val json = sharedPreferences.getString("$key.data", null) ?: return null

        return try {
            gson.fromJson<T>(json, type)
        } catch (_: Exception) {
            null
        }
    }
}