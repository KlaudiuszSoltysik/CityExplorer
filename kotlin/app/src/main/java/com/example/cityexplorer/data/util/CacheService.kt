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
    // 1. ZMIANA: @PublishedApi internal (wymagane dla funkcji inline)
    @PublishedApi
    internal val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 2. ZMIANA: Json zamiast Gson
    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true // Ważne: ignoruje nadmiarowe pola z API
        encodeDefaults = true
        coerceInputValues = true
    }

    // 3. ZMIANA: funkcja jest teraz 'inline' i 'reified'
    inline fun <reified T> saveToCache(key: String, version: String, data: T) {
        // encodeToString sam znajduje serializer dla T
        val jsonString = json.encodeToString(data)

        sharedPreferences.edit {
            putString(getVersionKey(key), version)
            putString(getDataKey(key), jsonString)
        }
    }

    fun getCachedVersion(key: String): String? {
        return sharedPreferences.getString(getVersionKey(key), null)
    }

    // 4. ZMIANA: usunięto parametr 'type: Type', jest teraz 'inline reified'
    inline fun <reified T> getCachedData(key: String): T? {
        val jsonString = sharedPreferences.getString(getDataKey(key), null) ?: return null

        return try {
            json.decodeFromString<T>(jsonString)
        } catch (_: Exception) {
            null
        }
    }

    // Te metody też muszą być dostępne dla inline
    @PublishedApi
    internal fun getVersionKey(baseKey: String) = "$baseKey.version"

    @PublishedApi
    internal fun getDataKey(baseKey: String) = "$baseKey.data"

    companion object {
        private const val PREFS_NAME = "app_data_cache"
    }
}

//@Singleton
//class CacheService @Inject constructor(
//    @ApplicationContext context: Context
//) {
//    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//    private val gson = Gson()
//
//    fun <T> saveToCache(key: String, version: String, data: T) {
//        sharedPreferences.edit {
//            putString(getVersionKey(key), version)
//            putString(getDataKey(key), gson.toJson(data))
//        }
//    }
//
//    fun getCachedVersion(key: String): String? {
//        return sharedPreferences.getString(getVersionKey(key), null)
//    }
//
//    fun <T> getCachedData(key: String, type: Type): T? {
//        val json = sharedPreferences.getString(getDataKey(key), null) ?: return null
//
//        return try {
//            gson.fromJson<T>(json, type)
//        } catch (_: Exception) {
//            null
//        }
//    }
//
//    private fun getVersionKey(baseKey: String) = "$baseKey.version"
//
//    private fun getDataKey(baseKey: String) = "$baseKey.data"
//
//    companion object {
//        private const val PREFS_NAME = "app_data_cache"
//    }
//}