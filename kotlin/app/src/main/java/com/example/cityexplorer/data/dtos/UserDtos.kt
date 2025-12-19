package com.example.cityexplorer.data.dtos

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponseDto(
    val isSuccess: Boolean,
    val token: String?
)

@Serializable
data class GetUserResponseDto(
    val isAuthorized: Boolean
)

@Serializable
data class GetUserStatisticsDto(
    val explored: Double,
    val progress: Int,
    val hexagonCount: Int,
    val playTime: Int,
    val distance: Int,
    val ranking: Int,
    val userCount: Int
)
