package io.elephantchess.engines.protocol

import io.elephantchess.engines.protocol.model.InfoLineResult
import io.elephantchess.engines.protocol.model.InfoLineResult.Companion.parseInfoLine
import io.elephantchess.engines.protocol.model.InfoLinesResult

class InfoLineListener : LineListener {

    private var deepestDepth: Int? = null
    private var bestMove: String? = null
    private var ponder: String? = null
    private var checkmate: Boolean = false
    private val infoLines = mutableListOf<InfoLineResult>()

    override fun receivedLine(line: String) {
        val infoLine = parseInfoLine(line)
        infoLines += infoLine

        if (line.startsWith("info")) {
            if (line.contains("depth") && line.contains("time")) {
                if (infoLine.depth != null) {
                    if (deepestDepth == null || deepestDepth!! < infoLine.depth) {
                        deepestDepth = infoLine.depth
                    }
                }
            }
        } else if (line.startsWith("bestmove")) {
            if (line == "bestmove (none)") {
                checkmate = true
            } else {
                val words = line.split(" ")
                words.forEachIndexed { i, word ->
                    when (word) {
                        "bestmove" -> bestMove = words[i + 1]
                        "ponder" -> ponder = words[i + 1]
                    }
                }
            }
        }
    }

    fun shouldStopSearch(depth : Int): Boolean {
        return (deepestDepth != null && deepestDepth!! >= depth) || bestMove != null || checkmate
    }

    fun getResult(): InfoLinesResult {
        return InfoLinesResult(
            infoLines = infoLines,
            checkmate = checkmate,
            bestMove = bestMove,
            ponder = ponder
        )
    }

}
