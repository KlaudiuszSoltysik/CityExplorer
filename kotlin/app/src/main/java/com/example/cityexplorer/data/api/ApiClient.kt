package com.example.cityexplorer.data.api

import android.annotation.SuppressLint
import com.example.cityexplorer.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object ApiClient {
    private const val API_KEY = BuildConfig.API_KEY
    private val json = Json { ignoreUnknownKeys = true }

    private val apiKeyInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("X-Api-Key", API_KEY)
            .build()
        chain.proceed(newRequest)
    }

    private val retrofit: Retrofit by lazy {
        val client = if (BuildConfig.DEBUG) {
            getUnsafeOkHttpClient()
        } else {
            OkHttpClient.Builder()
                .addInterceptor(apiKeyInterceptor)
                .build()
        }

        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val hexagonApiClient: HexagonApiClient by lazy {
        retrofit.create(HexagonApiClient::class.java)
    }

    val userApiClient: UserApiClient by lazy {
        retrofit.create(UserApiClient::class.java)
    }

    val versionApiClient: VersionApiClient by lazy {
        retrofit.create(VersionApiClient::class.java)
    }

    // Intentionally bypasses SSL verification for local/dev environments
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<X509TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0])
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(apiKeyInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
