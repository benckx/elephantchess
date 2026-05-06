package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.ChatMessageDaoService
import io.elephantchess.servicelayer.dto.admin.LastChatMessageResponse
import io.elephantchess.servicelayer.services.UserCache

class AdminChatService(
    private val userCache: UserCache,
    private val chatMessageDaoService: ChatMessageDaoService,
) {

    suspend fun listLastChatMessages(): LastChatMessageResponse {
        return chatMessageDaoService
            .listLastMessages(200)
            .map { record ->
                LastChatMessageResponse.ChatMessage(
                    gameId = record.gameId,
                    index = record.index,
                    author = userCache.get(record.author)!!.let { userDto ->
                        LastChatMessageResponse.Author(
                            userType = userDto.userType,
                            userId = userDto.userId,
                            username = userDto.username,
                        )
                    },
                    messageTime = record.messageTime.toEpochMilliseconds(),
                    content = record.content,
                )
            }
            .let { messages ->
                LastChatMessageResponse(messages)
            }
    }

}
