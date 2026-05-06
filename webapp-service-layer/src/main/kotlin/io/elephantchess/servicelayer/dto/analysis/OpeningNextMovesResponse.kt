package io.elephantchess.servicelayer.dto.analysis

data class OpeningNextMovesResponse(val entries: List<Entry>, val moves: List<String>) {

    data class Entry(
        val nextMove: String,
        val occurrences: Int,
        val redWinsRate: Float,
        val blackWinsRate: Float
    )

}
