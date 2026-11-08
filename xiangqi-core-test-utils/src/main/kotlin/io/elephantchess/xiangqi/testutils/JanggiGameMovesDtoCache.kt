package io.elephantchess.xiangqi.testutils

data class JanggiGameMovesDto(
    val gameId: String,
    val uciMoves: List<String>,
    val outcome: String? = null,
    val blueSetup: String = "INNER_ELEPHANT",
    val redSetup: String = "INNER_ELEPHANT",
)

/** Loads id;outcome;blueSetup;redSetup;moves rows. */
class JanggiGameMovesDtoCache {

    fun listAll(): List<JanggiGameMovesDto> =
        cache.toList()

    fun randomGame(): JanggiGameMovesDto =
        cache.random()

    fun randomGame(predicate: (JanggiGameMovesDto) -> Boolean): JanggiGameMovesDto =
        cache.filter(predicate).randomOrNull()
            ?: error("No Janggi game matches the requested conditions")

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
            require(pieces.size == 5) { "Invalid Janggi fixture columns: $line" }
            val gameId = pieces[0]
            val moves = pieces[4].split(",").filter { it.isNotBlank() }
            require(moves.all { movePattern.matches(it) }) {
                "Invalid zero-based Janggi coordinates in game '$gameId'"
            }
            val outcome = pieces[1].takeIf { it.isNotBlank() }
            val blueSetup = pieces[2]
            val redSetup = pieces[3]
            require(blueSetup.isNotBlank() && redSetup.isNotBlank()) { "Missing Janggi setup in game '$gameId'" }
            return JanggiGameMovesDto(gameId, moves, outcome, blueSetup, redSetup)
        }
    }
}
