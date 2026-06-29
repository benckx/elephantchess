package io.elephantchess.servicelayer.dto.analysis

import io.elephantchess.model.MoveAnnotationCategory

data class MoveAnnotationResult(
    val category: MoveAnnotationCategory,
    val cpl: Int,
    val engineCp: Int,
    val actualMoveCp: Int,
)

data class MoveAnnotationDetails(
    val moveIndex: Int,
    val category: MoveAnnotationCategory,
    val cpl: Int,
    val engineCp: Int,
    val actualMoveCp: Int,
)

data class AnnotationAggregate(
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

data class MoveAnnotationSummary(
    val annotatedMoves: Int,
    val neutralMoves: Int,
    val skippedMoves: Int,
    val categoryTotals: Map<MoveAnnotationCategory, AnnotationAggregate>,
) {
    val totalMoves: Int
        get() = annotatedMoves + neutralMoves + skippedMoves
}

