package io.elephantchess.xiangqi.testutils

class ManchuGameMovesDtoCache {

    fun listAll(): List<GameMovesDto> = cache.toList()

    fun findByGameId(gameId: String) = cache.find { it.gameId == gameId }
        ?: error("No Manchu game found with id '$gameId'")

    private companion object {

        val cache by lazy { loadAll() }

        fun loadAll(): List<GameMovesDto> {
            return ResourcesUtils.getResourceAsText("/manchu.txt")
                .split("\n")
                .filterNot { line -> line.isBlank() }
                .map { line ->
                    val (gameId, moves) = line.split(";")
                    GameMovesDto(gameId, moves.split(","))
                }
        }

    }

}
