package io.elephantchess.engines.protocol.model

data class InfoLinesResult(
    val infoLines: List<InfoLineResult>,
    val checkmate: Boolean,
    val bestMove: String?,
    val ponder: String?,
) {

    fun maxDepth(): Int? {
        return infoLines.mapNotNull { infoLine -> infoLine.depth }.maxOrNull()
    }

    fun deepestResultCentiPawns(): Int? {
        return infoLines
            .filter { line -> line.cp != null }
            .sortedBy { line -> line.depth }
            .lastOrNull()?.cp
    }

    /**
     * @return [InfoLineResult] with the greatest depth
     */
    fun deepestResult(): InfoLineResult? {
        return infoLines
            .sortedBy { line -> line.depth }
            .lastOrNull()
    }

    override fun toString(): String {
        val elements = mutableListOf<String>()
        elements += "checkmate=${checkmate}"
        if (bestMove != null) {
            elements += "bestMove=$bestMove"
        }
        if (ponder != null) {
            elements += "ponder=$ponder"
        }
        val mateInNLines = infoLines.filter { infoLine -> infoLine.mate != null }
        if (mateInNLines.isNotEmpty()) {
            val line = mateInNLines.last()
            elements += "mate=${line.mate}"
            elements += "depth=${line.depth}"
            elements += "pv=${line.pv.joinToString(",")}"
        }
        maxDepth()?.let { depth ->
            elements += "depth=$depth"
        }
        return "InfoResults [${elements.joinToString("; ")}]"
    }

}
