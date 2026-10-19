package io.elephantchess.scripts.openings

import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.model.Outcome
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED
import org.jooq.DSLContext

internal suspend fun loadGamesWithMoves(
    readContext: DSLContext,
    referenceGameDaoService: ReferenceGameDaoService,
    onProgress: (Int) -> Unit = {}
): MutableList<ReferenceGameDto> {
    val memoryCache = mutableListOf<ReferenceGameDto>()
    listAllGames(readContext).forEachIndexed { i, game ->
        val moves = referenceGameDaoService.listMoves(game.id)
        if (moves.isNotEmpty()) {
            memoryCache += game.copy(moves = moves)
            if (i % 1_000 == 0) {
                onProgress(i)
            }
        }
    }
    return memoryCache
}

private suspend fun listAllGames(readContext: DSLContext): List<ReferenceGameDto> {
    return readContext
        .select(
            REFERENCE_GAME.ID,
            REFERENCE_GAME.OUTCOME,
            REFERENCE_GAME.RED_PLAYER,
            REFERENCE_GAME.BLACK_PLAYER
        )
        .from(REFERENCE_GAME)
        .awaitRecords()
        .map { record ->
            ReferenceGameDto(
                id = record.get(REFERENCE_GAME.ID),
                outcome = record.get(REFERENCE_GAME.OUTCOME),
                redPlayer = record.get(REFERENCE_GAME.RED_PLAYER),
                blackPlayer = record.get(REFERENCE_GAME.BLACK_PLAYER),
                moves = emptyList()
            )
        }
        .toList()
}

internal data class ReferenceGameDto(
    val id: String,
    val outcome: Outcome,
    val redPlayer: String?,
    val blackPlayer: String?,
    val moves: List<String>
) {
    fun playerColor(playerId: String): Color? {
        return when (playerId) {
            redPlayer -> RED
            blackPlayer -> BLACK
            else -> null
        }
    }
}
