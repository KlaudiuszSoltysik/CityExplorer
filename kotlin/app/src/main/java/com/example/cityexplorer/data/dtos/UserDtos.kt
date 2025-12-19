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
    val progress: Int,
    val playTime: Int,
    val ranking: Int,
    val userCount: Int
)