package com.example.cityexplorer.data.dtos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetCurrentVersionRequestDto(
    @SerialName("key")
    val key: String = ""
)

@Serializable
data class GetCurrentVersionResponseDto(
    @SerialName("version")
    val version: String = ""
)