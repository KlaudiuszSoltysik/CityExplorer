@file:Suppress("DEPRECATION")

package com.example.cityexplorer.data.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenService @Inject constructor(
    @ApplicationContext val context: Context
) {
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (_: Exception) {
            deleteSharedPreferences()
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deleteSharedPreferences() {
        context.deleteSharedPreferences(PREFS_FILENAME)
    }

    fun saveToken(token: String) {
        sharedPreferences.edit {
            putString(KEY_JWT_TOKEN, token)
        }
    }

    fun getToken(): String? {
        return sharedPreferences.getString(KEY_JWT_TOKEN, null)
    }

    fun clearToken() {
        sharedPreferences.edit {
            remove(KEY_JWT_TOKEN)
        }
    }

    companion object {
        private const val PREFS_FILENAME = "secure_prefs"
        private const val KEY_JWT_TOKEN = "jwt_token"
    }
}
