package com.example.cityexplorer.data.dtos

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class LoginResponseDto(
    val isSuccess: Boolean,
    val token: String?
)

@Serializable
@Keep
data class GetUserResponseDto(
    val isAuthorized: Boolean
)

@Serializable
@Keep
data class GetUserStatisticsDto(
    val explored: Double,
    val progress: Int,
    val hexagonCount: Int,
    val playTime: Int,
    val distance: Int,
    val ranking: Int,
    val userCount: Int
)
