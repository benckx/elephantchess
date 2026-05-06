package io.elephantchess.servicelayer.dto.game

import io.elephantchess.servicelayer.dto.ChatMessage

data class GetChatMessageHistoryResponse(
    val messages: List<ChatMessage>,
)
