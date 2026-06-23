package io.elephantchess.servicelayer.dto.analysis

data class OpeningNextMovesResponse(val entries: List<Entry>, val moves: List<String>) {

    data class Entry(
        val nextMove: String,
        val occurrences: Int,
        val redWinsRate: Float,
        val blackWinsRate: Float,
        /**
         * Share of this move within the general population (all reference games) at the same position,
         * or `null` when not applicable (e.g. the analysis endpoint, which already reports the general
         * population share directly).
         */
        val generalPopulationRate: Float? = null,
    )

}
