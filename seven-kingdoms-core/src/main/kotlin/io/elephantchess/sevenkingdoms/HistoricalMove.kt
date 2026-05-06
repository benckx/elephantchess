package io.elephantchess.sevenkingdoms

data class HistoricalMove(
    val from: Position,
    val to: Position,
    val color: Color,
    val capture: PieceAtPosition?,
    val armyCapturedEvent: ArmyCapturedEvent? = null
) {

    init {
        // if there is an ArmyCaptureEvent, it was triggered by a simple capture
        if (armyCapturedEvent != null) {
            require(capture != null)
        }
    }

    val move: Move
        get() = Move(from, to)

    val uci
        get() = move.uci

    override fun toString(): String {
        var result = "$color played $move"
        if (capture != null) {
            result += " and captures ${capture.piece}"
        }

        return result
    }

}
