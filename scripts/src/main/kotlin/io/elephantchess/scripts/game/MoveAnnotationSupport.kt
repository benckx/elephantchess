package io.elephantchess.scripts.game

import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
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

internal val moveAnnotationCategoriesInOrder = listOf(
    MoveAnnotationCategory.BLUNDER,
    MoveAnnotationCategory.MISTAKE,
    MoveAnnotationCategory.INACCURACY,
    MoveAnnotationCategory.INTERESTING,
    MoveAnnotationCategory.GOOD,
    MoveAnnotationCategory.BRILLIANT,
)

internal data class MoveAnnotationResult(
    val category: MoveAnnotationCategory,
    val cpl: Int,
)

internal data class AnnotationAggregate(
    val count: Int = 0,
    val totalCpl: Long = 0,
    val minCpl: Int? = null,
    val maxCpl: Int? = null,
) {
    fun add(cpl: Int): AnnotationAggregate =
        copy(
            count = count + 1,
            totalCpl = totalCpl + cpl,
            minCpl = minCpl?.coerceAtMost(cpl) ?: cpl,
            maxCpl = maxCpl?.coerceAtLeast(cpl) ?: cpl,
        )

    fun merge(other: AnnotationAggregate): AnnotationAggregate =
        copy(
            count = count + other.count,
            totalCpl = totalCpl + other.totalCpl,
            minCpl = when {
                minCpl == null -> other.minCpl
                other.minCpl == null -> minCpl
                else -> minOf(minCpl, other.minCpl)
            },
            maxCpl = when {
                maxCpl == null -> other.maxCpl
                other.maxCpl == null -> maxCpl
                else -> maxOf(maxCpl, other.maxCpl)
            },
        )

    fun averageCpl(): Double? =
        if (count == 0) null else totalCpl.toDouble() / count.toDouble()
}

internal data class MoveAnnotationSummary(
    val annotatedMoves: Int,
    val neutralMoves: Int,
    val skippedMoves: Int,
    val categoryTotals: Map<MoveAnnotationCategory, AnnotationAggregate>,
) {
    val totalMoves: Int
        get() = annotatedMoves + neutralMoves + skippedMoves
}

internal fun summarizeMoveAnnotations(
    moves: List<String>,
    analysisMap: Map<String, InfoLineResultDto>,
    startFen: String = DEFAULT_START_FEN,
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

        when {
            previousNodeData == null || engineBestAnalysis == null || actualMoveAnalysis == null -> {
                skippedMoves++
            }

            else -> {
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
    } else {
        return when {
            cpl >= 300 -> MoveAnnotationCategory.BRILLIANT
            cpl >= 100 -> MoveAnnotationCategory.GOOD
            else -> MoveAnnotationCategory.INTERESTING
        }
    }
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
