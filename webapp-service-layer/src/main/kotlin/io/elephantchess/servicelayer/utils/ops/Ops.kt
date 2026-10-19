package io.elephantchess.servicelayer.utils.ops

import io.elephantchess.db.dao.codegen.tables.pojos.Game
import io.elephantchess.db.utils.ratingUpdateRecord
import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.EngineId
import io.elephantchess.engines.process.FairyStockfishEngineId
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.engines.protocol.model.InfoLinesResult
import io.elephantchess.servicelayer.dto.game.RatingUpdate
import io.elephantchess.xiangqi.AbstractPieceType
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("Ops")

fun Game.ratingUpdate(): RatingUpdate? {
    return ratingUpdateRecord()?.let { record ->
        return RatingUpdate(
            isRated = record.isRated,
            inviterRatingFrom = record.inviterRatingFrom,
            inviterRatingTo = record.inviterRatingTo,
            inviteeRatingFrom = record.inviteeRatingFrom,
            inviteeRatingTo = record.inviteeRatingTo,
        )
    }
}

fun isNonStandardFen(fen: String): Boolean {
    try {
        val board = Board(fen)
        Color.entries.forEach { color ->
            val allPieces = board.listAllPieces(color)
            AbstractPieceType.entries.forEach { pieceType ->
                val countPerType = allPieces.count { it.abstractPieceType() == pieceType }
                if (countPerType > pieceType.maxLegal) {
                    return true
                }
            }
        }

        return false
    } catch (_: Exception) {
        return true
    }
}

suspend fun EnginePool.safeQueryForDepth(
    fen: String,
    engineId: EngineId,
    depth: Int,
    timeout: Long = 60_000,
    variant: Variant = Variant.XIANGQI,
): InfoLinesResult? {
    var safeEngineId = engineId
    if (engineId == PikafishEngineId && (variant == Variant.MANCHU || isNonStandardFen(fen))) {
        logger.warn { "non-standard FEN or Manchu variant detected, forcing use of Fairy Stockfish instead of Pikafish $fen" }
        safeEngineId = FairyStockfishEngineId
    }
    return queryForDepth(
        fen = fen,
        engineId = safeEngineId,
        depth = depth,
        timeout = timeout,
        variant = variant,
    )
}
