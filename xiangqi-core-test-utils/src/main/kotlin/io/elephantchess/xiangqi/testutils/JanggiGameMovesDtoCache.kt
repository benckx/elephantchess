package io.elephantchess.xiangqi.testutils

class JanggiGameMovesDtoCache {

    fun listAll(): List<GameMovesDto> =
        cache.toList()

    fun randomGame(): GameMovesDto =
        cache.random()

    fun findByGameId(gameId: String) =
        cache.find { it.gameId == gameId }
            ?: error("No Janggi game found with id '$gameId'")

    private companion object {

        val engineMovePattern = Regex("([a-i])(10|[1-9])([a-i])(10|[1-9])")
        val cache by lazy { loadAll() }

        fun loadAll(): List<GameMovesDto> =
            ResourcesUtils.getResourceAsText("/janggi.txt")
                .split("\n")
                .filterNot { line -> line.isBlank() }
                .map { line ->
                    val pieces = line.split(";", limit = 3)
                    val gameId = pieces[0]
                    // Fairy-Stockfish encodes Janggi ranks as 1..10, but the core board uses 0..9.
                    // We normalize engine coordinates on load so callers always work with the internal UCI format.
                    val moves = pieces[1].split(",").filter { it.isNotBlank() }.map { move ->
                        val match = requireNotNull(engineMovePattern.matchEntire(move)) {
                            "Invalid Janggi move '$move' in game '$gameId'"
                        }
                        val (fromFile, fromRank, toFile, toRank) = match.destructured
                        "$fromFile${fromRank.toInt() - 1}$toFile${toRank.toInt() - 1}"
                    }
                    val outcome = pieces.getOrNull(2)?.takeIf { it.isNotBlank() }
                    GameMovesDto(gameId, moves, outcome)
                }
    }
}
