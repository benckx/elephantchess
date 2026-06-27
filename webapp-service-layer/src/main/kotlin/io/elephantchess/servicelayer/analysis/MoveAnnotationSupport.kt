package io.elephantchess.servicelayer.analysis

import io.elephantchess.model.dto.analysis.AnnotationAggregate
import io.elephantchess.model.dto.analysis.MoveAnnotationCategory
import io.elephantchess.model.dto.analysis.MoveAnnotationDetails
import io.elephantchess.model.dto.analysis.MoveAnnotationResult
import io.elephantchess.model.dto.analysis.MoveAnnotationSummary
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

val moveAnnotationCategoriesInOrder = listOf(
    MoveAnnotationCategory.BLUNDER,
    MoveAnnotationCategory.MISTAKE,
    MoveAnnotationCategory.INACCURACY,
    MoveAnnotationCategory.INTERESTING,
    MoveAnnotationCategory.GOOD,
    MoveAnnotationCategory.BRILLIANT,
)

fun summarizeMoveAnnotations(
    moves: List<String>,
    analysisMap: Map<String, InfoLineResultDto>,
    startFen: String = DEFAULT_START_FEN,
    actualMoveFilter: (InfoLineResultDto?) -> Boolean = { true },
): MoveAnnotationSummary {
    val board = Board(startFen)
    var annotatedMoves = 0
    var neutralMoves = 0
    var skippedMoves = 0
    val categoryTotals = moveAnnotationCategoriesInOrder
        .associateWith { AnnotationAggregate() }
        .toMutableMap()

    moves.forEach { move ->
        val previousNodeData = analysisMap[resetFullMoveCount(board.outputFen())]
        val engineBestAnalysis = previousNodeData?.let { findAnalysisDataFromEngineBestMove(analysisMap, it) }

        board.registerMove(move)
        val actualMoveAnalysis = analysisMap[resetFullMoveCount(board.outputFen())]
        if (!actualMoveFilter(actualMoveAnalysis)) {
            return@forEach
        }

        when {
            previousNodeData == null || engineBestAnalysis == null || actualMoveAnalysis == null -> {
                skippedMoves++
            }

            else -> {
                if (!hasComparableAnalysisData(engineBestAnalysis, actualMoveAnalysis)) {
                    skippedMoves++
                    return@forEach
                }

                val annotation = calculateMoveAnnotation(engineBestAnalysis, actualMoveAnalysis)
                if (annotation == null) {
                    neutralMoves++
                } else {
                    categoryTotals[annotation.category] = categoryTotals.getValue(annotation.category).add(annotation.cpl)
                    annotatedMoves++
                }
            }
        }
    }

    return MoveAnnotationSummary(
        annotatedMoves = annotatedMoves,
        neutralMoves = neutralMoves,
        skippedMoves = skippedMoves,
        categoryTotals = categoryTotals,
    )
}

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

fun calculateCpl(engineBest: InfoLineResultDto?, actualMove: InfoLineResultDto?): Int? {
    if (engineBest == null || actualMove == null || actualMove.isCheckmate || !hasComparableAnalysisData(engineBest, actualMove)) {
        return null
    }

    val engineCp = heuristicCp(engineBest) ?: return null
    val actualMoveCp = heuristicCp(actualMove) ?: return null
    return engineCp - actualMoveCp
}

fun moveAnnotationCategoryFromCpl(cpl: Int): MoveAnnotationCategory? {
    if (abs(cpl) < 50) {
        return null
    } else if (cpl < 0) {
        val deltaLoss = abs(cpl)
        return when {
            deltaLoss >= 300 -> MoveAnnotationCategory.BLUNDER
            deltaLoss >= 100 -> MoveAnnotationCategory.MISTAKE
            else -> MoveAnnotationCategory.INACCURACY
        }
    } else {
        return when {
            cpl >= 300 -> MoveAnnotationCategory.BRILLIANT
            cpl >= 100 -> MoveAnnotationCategory.GOOD
            else -> MoveAnnotationCategory.INTERESTING
        }
    }
}

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

private fun hasComparableAnalysisData(
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
