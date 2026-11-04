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

        val cache by lazy { loadAll() }

        fun loadAll(): List<GameMovesDto> =
            ResourcesUtils.getResourceAsText("/janggi.txt")
                .split("\n")
                .filterNot { line -> line.isBlank() }
                .map { line ->
                    val (gameId, moves) = line.split(";")
                    GameMovesDto(gameId, moves.split(","))
                }
    }
}
