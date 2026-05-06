package io.elephantchess.servicelayer.clients.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordMessageResponse(
    val id: String,
    @SerialName("channel_id") val channelId: String,
    val timestamp: String
)
