package io.elephantchess.xiangqi.testutils

class GameMovesDtoCache {

    fun listAll(): List<GameMovesDto> = cache.toList()

    fun findByGameId(gameId: String) = cache.find { it.gameId == gameId }!!

    private companion object {

        val cache by lazy { loadAll() }

        fun loadAll(): List<GameMovesDto> {
            return ResourcesUtils.getResourceAsText("/uci.txt")
                .split("\n")
                .filterNot { line -> line.isBlank() }
                .map { line ->
                    val (gameId, moves) = line.split(";")
                    GameMovesDto(gameId, moves.split(","))
                }
        }

    }

}
