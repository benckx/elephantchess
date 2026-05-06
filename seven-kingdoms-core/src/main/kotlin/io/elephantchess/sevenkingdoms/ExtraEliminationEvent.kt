package io.elephantchess.sevenkingdoms

/**
 * Elimination event due to external factors (player resigned or flagged)
 */
data class ExtraEliminationEvent(
    val index : Int,
    val colors : List<Color>
)
