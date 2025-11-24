package com.example.cityexplorer.data.api

import android.annotation.SuppressLint
import com.example.cityexplorer.data.util.Constants
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object ApiClient {
    private const val BASE_URL = Constants.BASE_URL

    private val json = Json { ignoreUnknownKeys = true }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val hexagonApiService: HexagonApiService by lazy {
        retrofit.create(HexagonApiService::class.java)
    }
}

@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
fun getUnsafeOkHttpClient(): OkHttpClient {
    val trustAllCerts = arrayOf<X509TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, SecureRandom())

    val hostnameVerifier = HostnameVerifier { _, _ -> true }

    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0])
        .hostnameVerifier(hostnameVerifier)
        .build()
}
