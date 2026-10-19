package io.elephantchess.servicelayer.dto.ws

data class PlayerVsPlayerInput(
    val message: String? = null,
    val isTyping: Boolean = false,
)
