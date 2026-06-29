package io.elephantchess.servicelayer.utils

import io.elephantchess.model.MoveAnnotationCategory
import io.elephantchess.servicelayer.dto.analysis.MoveAnnotationDetails
import io.elephantchess.servicelayer.dto.analysis.MoveAnnotationResult
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import kotlin.math.abs

// Mirrors MAX_ABS_CP in webapp/src/main/resources/public/js/modules/engine.js
private const val MAX_ABS_CP = 7_706

// This is the minimum depth for any comparable annotation calculation; callers can still
// apply stricter reporting filters separately (for example, the script's depth-20 split).
private const val MIN_COMPARABLE_ANALYSIS_DEPTH = 18

/**
 * Replays the game move list from [startFen] and returns the backend annotation details for each
 * move that can be compared against the engine's best continuation from the preceding position.
 *
 * The returned list only contains annotated moves; neutral or incomparable moves are omitted.
 */
fun collectMoveAnnotations(
    moves: List<String>,
    analysisMap: Map<String, InfoLineResultDto>,
    startFen: String = DEFAULT_START_FEN,
): List<MoveAnnotationDetails> {
    val board = Board(startFen)
    val annotations = mutableListOf<MoveAnnotationDetails>()

    moves.forEachIndexed { index, move ->
        val previousNodeData = analysisMap[resetFullMoveCount(board.outputFen())]
        val engineBestAnalysis = previousNodeData?.let { findAnalysisDataFromEngineBestMove(analysisMap, it) }

        board.registerMove(move)
        val actualMoveAnalysis = analysisMap[resetFullMoveCount(board.outputFen())]

        val annotation = calculateMoveAnnotation(engineBestAnalysis, actualMoveAnalysis)
        if (annotation != null) {
            annotations += MoveAnnotationDetails(
                moveIndex = index,
                category = annotation.category,
                cpl = annotation.cpl,
                engineCp = annotation.engineCp,
                actualMoveCp = annotation.actualMoveCp,
            )
        }
    }

    return annotations
}

/**
 * Builds the full move-annotation result for a played move by combining the comparable heuristic
 * scores, CPL delta, and the annotation category derived from that delta.
 *
 * Returns `null` whenever the two analysis lines cannot be compared or when the CPL falls into the
 * neutral range.
 */
fun calculateMoveAnnotation(engineBest: InfoLineResultDto?, actualMove: InfoLineResultDto?): MoveAnnotationResult? {
    val engineCp = engineBest?.let(::heuristicCp) ?: return null
    val actualMoveCp = actualMove?.let(::heuristicCp) ?: return null
    val cpl = calculateCpl(engineBest, actualMove) ?: return null
    val category = moveAnnotationCategoryFromCpl(cpl) ?: return null

    return MoveAnnotationResult(
        category = category,
        cpl = cpl,
        engineCp = engineCp,
        actualMoveCp = actualMoveCp,
    )
}

/**
 * Computes the centipawn-loss style delta between the engine's preferred continuation and the move
 * that was actually played.
 *
 * The comparison is only valid when both analysis lines are deep enough, have matching depth, and
 * expose a heuristic score. Checkmate actual moves are excluded from this calculation.
 */
fun calculateCpl(engineBest: InfoLineResultDto?, actualMove: InfoLineResultDto?): Int? {
    if (engineBest == null || actualMove == null || actualMove.isCheckmate || !hasComparableAnalysisData(
            engineBest, actualMove
        )
    ) {
        return null
    }

    val engineCp = heuristicCp(engineBest) ?: return null
    val actualMoveCp = heuristicCp(actualMove) ?: return null
    return engineCp - actualMoveCp
}

/**
 * Maps a CPL delta to the user-facing annotation category thresholds shared by the backend, script,
 * and frontend consumers.
 *
 * Small deltas inside the neutral band return `null`; negative deltas represent missed chances and
 * positive deltas represent stronger-than-expected moves.
 */
fun moveAnnotationCategoryFromCpl(cpl: Int): MoveAnnotationCategory? {
    return if (abs(cpl) < 50) {
        null
    } else if (cpl < 0) {
        val deltaLoss = abs(cpl)
        when {
            deltaLoss >= 300 -> MoveAnnotationCategory.BLUNDER
            deltaLoss >= 100 -> MoveAnnotationCategory.MISTAKE
            else -> MoveAnnotationCategory.INACCURACY
        }
    } else {
        when {
            cpl >= 300 -> MoveAnnotationCategory.BRILLIANT
            cpl >= 100 -> MoveAnnotationCategory.GOOD
            else -> MoveAnnotationCategory.INTERESTING
        }
    }
}

/**
 * Looks up the analysis line that corresponds to the engine's best move from [previousNodeData].
 *
 * This follows the best move from the previous position, rebuilds the resulting FEN, normalizes its
 * full-move count, and uses that normalized FEN as the key into [analysisMap].
 */
fun findAnalysisDataFromEngineBestMove(
    analysisMap: Map<String, InfoLineResultDto>,
    previousNodeData: InfoLineResultDto,
): InfoLineResultDto? {
    val bestMove = previousNodeData.bestMove ?: return null
    val board = Board(previousNodeData.fen)
    board.registerMove(bestMove)
    val resultingFen = resetFullMoveCount(board.outputFen())
    return analysisMap[resultingFen]
}

/**
 * Normalizes either a centipawn score or a mate score into a bounded heuristic centipawn value so
 * different engine score types can still be compared on one axis.
 *
 * Mate scores are pushed beyond the regular centipawn ceiling, with shorter mates ranked higher.
 */
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

/**
 * Checks whether two engine info lines are safe to compare for annotation purposes.
 *
 * Both lines must have a depth, reach the minimum comparable depth, match each other's depth, and
 * expose a heuristic score after cp/mate normalization.
 */
fun hasComparableAnalysisData(
    engineBest: InfoLineResultDto,
    actualMove: InfoLineResultDto,
): Boolean {
    val engineDepth = engineBest.depth ?: return false
    val actualDepth = actualMove.depth ?: return false

    return engineDepth >= MIN_COMPARABLE_ANALYSIS_DEPTH &&
            actualDepth >= MIN_COMPARABLE_ANALYSIS_DEPTH &&
            engineDepth == actualDepth &&
            heuristicCp(engineBest) != null &&
            heuristicCp(actualMove) != null
}
