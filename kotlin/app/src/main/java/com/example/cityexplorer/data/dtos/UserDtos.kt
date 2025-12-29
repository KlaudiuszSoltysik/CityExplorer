package com.example.cityexplorer.data.dtos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ValidateLoginTokenRequestDto(
    @SerialName("googleToken")
    val googleToken: String
)

@Serializable
data class ValidateLoginTokenResponseDto(
    @SerialName("isSuccess")
    val isSuccess: Boolean,

    @SerialName("token")
    val token: String
)

@Serializable
data class ValidateAuthorizationTokenResponseDto(
    @SerialName("id")
    val id: String = ""
)

@Serializable
data class GetUserStatisticsResponseDto(
    @SerialName("explored")
    val explored: Double,

    @SerialName("progress")
    val progress: Int,

    @SerialName("hexagonCount")
    val hexagonCount: Int,

    @SerialName("playTime")
    val playTime: Int,

    @SerialName("distance")
    val distance: Int,

    @SerialName("ranking")
    val ranking: Int,

    @SerialName("userCount")
    val userCount: Int
)
