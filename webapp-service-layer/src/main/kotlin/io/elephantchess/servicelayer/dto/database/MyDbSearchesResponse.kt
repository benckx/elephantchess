package io.elephantchess.servicelayer.dto.database

import io.elephantchess.xiangqi.Color

data class MyDbSearchesResponse(val entries: List<Entry>) {

    data class Entry(
        val queryId: String,
        val updateTime: Long,
        val playerName: String?,
        val playerColor: Color?,
        val eventName: String?,
        val searchStart: String?,
        val searchEnd: String?,
        val fen: String?,
        val numberOfResults: Int,
    )

}
