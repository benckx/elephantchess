package io.elephantchess.xiangqi.testutils

import io.elephantchess.xiangqi.JanggiSetup
import io.elephantchess.xiangqi.generateJanggiStartFen

data class JanggiGameMovesDto(
    val gameId: String,
    val uciMoves: List<String>,
    val outcome: String? = null,
    val blueSetup: JanggiSetup = JanggiSetup.INNER_ELEPHANT,
    val redSetup: JanggiSetup = JanggiSetup.INNER_ELEPHANT,
) {
    val startFen: String
        get() = generateJanggiStartFen(blueSetup, redSetup)
}

/** Loads id;moves;outcome;blueSetup;redSetup rows. Legacy rows use INNER_ELEPHANT for both sides. */
class JanggiGameMovesDtoCache {

    fun listAll(): List<JanggiGameMovesDto> =
        cache.toList()

    fun randomGame(): JanggiGameMovesDto =
        cache.random()

    fun findByGameId(gameId: String) =
        cache.find { it.gameId == gameId }
            ?: error("No Janggi game found with id '$gameId'")

    internal companion object {

        val movePattern = Regex("[a-i][0-9][a-i][0-9]")
        val cache by lazy { loadAll() }

        private fun loadAll(): List<JanggiGameMovesDto> =
            ResourcesUtils.getResourceAsText("/janggi.txt")
                .split("\n")
                .filterNot { line -> line.isBlank() }
                .map { parseLine(it) }

        fun parseLine(line: String): JanggiGameMovesDto {
            val pieces = line.split(";")
            require(pieces.size in setOf(2, 3, 5)) { "Invalid Janggi fixture columns: $line" }
            val gameId = pieces[0]
            val moves = pieces[1].split(",").filter { it.isNotBlank() }
            require(moves.all { movePattern.matches(it) }) {
                "Invalid zero-based Janggi coordinates in game '$gameId'"
            }
            val outcome = pieces.getOrNull(2)?.takeIf { it.isNotBlank() }
            val blueSetup = pieces.getOrNull(3)?.let { JanggiSetup.valueOf(it) } ?: JanggiSetup.INNER_ELEPHANT
            val redSetup = pieces.getOrNull(4)?.let { JanggiSetup.valueOf(it) } ?: JanggiSetup.INNER_ELEPHANT
            return JanggiGameMovesDto(gameId, moves, outcome, blueSetup, redSetup)
        }
    }
}
