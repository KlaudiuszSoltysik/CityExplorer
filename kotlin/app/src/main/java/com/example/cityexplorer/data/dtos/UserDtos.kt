package com.example.cityexplorer.data.dtos

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val token: String
)

@Serializable
data class LoginResponseDto(
    val isSuccess: Boolean,
    val token: String?
)