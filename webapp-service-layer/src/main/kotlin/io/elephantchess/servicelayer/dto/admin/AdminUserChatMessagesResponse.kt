package io.elephantchess.servicelayer.dto.admin

data class AdminUserChatMessagesResponse(val entries: List<Entry>) {

    data class Entry(
        val gameId: String,
        val messageTime: Long,
        val content: String,
    )
}
