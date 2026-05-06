package io.elephantchess.servicelayer.clients.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordMessage(
    val content: String,
    val tts: Boolean = false,
    @SerialName("message_reference") val messageReference: MessageReference? = null
) {

    @Serializable
    data class MessageReference(
        val type: Int = 0,
        @SerialName("message_id") val messageId: String
    )

}
