package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.UserType
import io.elephantchess.xiangqi.Color

data class ReferenceGameSearchQueryResponse(val entries: List<Entry>) {

    data class Entry(
        val queryId: String,
        val queryTime: Long,
        val userId: String,
        val userType: UserType,
        val username: String,
        val searchStart: String?,
        val searchEnd: String?,
        val playerName: String?,
        val playerColor: Color?,
        val eventName: String?,
        val fen: String?,
        val offset: Int?,
        val numberOfResults: Int
    )

}
