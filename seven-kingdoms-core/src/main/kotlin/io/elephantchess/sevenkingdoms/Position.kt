package io.elephantchess.sevenkingdoms

data class Position(val x: Int, val y: Int) {

    val isEmperor: Boolean
        get() = x == EMPEROR_X && y == EMPEROR_Y

    val isOnBoard: Boolean
        get() = x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE

    val uci: String
        get() = "${('a' + x)}${y}"

    val algebraic: String
        get() = "${('a' + x)}${y + 1}"

    val left: Position
        get() = allLeftFor(1)[0]

    val right: Position
        get() = allRightFor(1)[0]

    val top: Position
        get() = allTopFor(1)[0]

    val bottom: Position
        get() = allBottomFor(1)[0]

    fun allTopFor(n: Int) =
        (1..n).map { Position(x, y + it) }

    fun allBottomFor(n: Int) =
        (1..n).map { Position(x, y - it) }

    fun allLeftFor(n: Int) =
        (1..n).map { Position(x - it, y) }

    fun allRightFor(n: Int) =
        (1..n).map { Position(x + it, y) }

    fun allTopLeftDiagonalsFor(n: Int) =
        (1..n).map { Position(x - it, y + it) }

    fun allTopRightDiagonalsFor(n: Int) =
        (1..n).map { Position(x + it, y + it) }

    fun allBottomLeftDiagonalsFor(n: Int) =
        (1..n).map { Position(x - it, y - it) }

    fun allBottomRightDiagonalsFor(n: Int) =
        (1..n).map { Position(x + it, y - it) }

    override fun toString() = "($x, $y)"

    companion object {

        const val BOARD_SIZE = 19
        const val EMPEROR_X = 9
        const val EMPEROR_Y = 9

        fun listAll(): List<Position> {
            val positions = mutableListOf<Position>()
            for (x in 0 until BOARD_SIZE) {
                for (y in 0 until BOARD_SIZE) {
                    positions.add(Position(x, y))
                }
            }
            return positions
        }
    }

}
