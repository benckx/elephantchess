package io.elephantchess.scripts.game

import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import kotlin.math.abs

// Mirrors MAX_ABS_CP in webapp/src/main/resources/public/js/modules/engine.js
private const val MAX_ABS_CP = 7_706

internal enum class MoveAnnotationCategory {
    BLUNDER,
    MISTAKE,
    INACCURACY,
    INTERESTING,
    GOOD,
    BRILLIANT,
}

internal data class MoveAnnotationResult(
    val category: MoveAnnotationCategory,
    val cpl: Int,
)

internal fun calculateMoveAnnotation(engineBest: InfoLineResultDto?, actualMove: InfoLineResultDto?): MoveAnnotationResult? {
    val cpl = calculateCpl(engineBest, actualMove) ?: return null
    val category = moveAnnotationCategoryFromCpl(cpl) ?: return null
    return MoveAnnotationResult(category, cpl)
}

internal fun calculateCpl(engineBest: InfoLineResultDto?, actualMove: InfoLineResultDto?): Int? {
    if (engineBest == null || actualMove == null || actualMove.isCheckmate) {
        return null
    }

    val engineCp = heuristicCp(engineBest) ?: return null
    val actualMoveCp = heuristicCp(actualMove) ?: return null
    return engineCp - actualMoveCp
}

internal fun moveAnnotationCategoryFromCpl(cpl: Int): MoveAnnotationCategory? {
    if (abs(cpl) < 50) {
        return null
    } else if (cpl < 0) {
        val deltaLoss = abs(cpl)
        return when {
            deltaLoss >= 300 -> MoveAnnotationCategory.BLUNDER
            deltaLoss >= 100 -> MoveAnnotationCategory.MISTAKE
            else -> MoveAnnotationCategory.INACCURACY
        }
    } else if (cpl > 0) {
        return when {
            cpl >= 300 -> MoveAnnotationCategory.BRILLIANT
            cpl >= 100 -> MoveAnnotationCategory.GOOD
            else -> MoveAnnotationCategory.INTERESTING
        }
    }

    return null
}

internal fun findAnalysisDataFromEngineBestMove(
    analysisMap: Map<String, InfoLineResultDto>,
    previousNodeData: InfoLineResultDto,
): InfoLineResultDto? {
    val bestMove = previousNodeData.bestMove ?: return null
    val board = Board(previousNodeData.fen)
    board.registerMove(bestMove)
    val resultingFen = resetFullMoveCount(board.outputFen())
    return analysisMap[resultingFen]
}

private fun heuristicCp(infoLineResult: InfoLineResultDto): Int? {
    val cp = infoLineResult.cp
    val mate = infoLineResult.mate
    return when {
        cp != null -> cp.coerceIn(-MAX_ABS_CP, MAX_ABS_CP)
        mate != null -> {
            val maxMate = 40
            val mateBonusInCp = (maxMate - abs(mate)).coerceAtLeast(0) * 8
            if (mate < 0) -MAX_ABS_CP - mateBonusInCp else MAX_ABS_CP + mateBonusInCp
        }

        else -> null
    }
}
