package io.elephantchess.xiangqi

data class SequenceOfChecks(
    val color: Color,
    val attackers: Set<PhysicalPiece>,
    val moves: List<HistoricalMove>,
) {

    fun size() = moves.size

    fun fullMoves(): List<Int> = moves.map { it.fullMove() }

    fun isSubSetOf(other: SequenceOfChecks): Boolean {
        return other.fullMoves().containsAll(this.fullMoves()) && other.attackers.size > this.attackers.size
    }

    fun exceeds(rule: PerpetualCheckingRule): Boolean {
        return attackers.size == rule.numberOfPieces && moves.size >= rule.numberOfChecks
    }

}
