package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.AbstractPieceType.*
import io.elephantchess.xiangqi.Color.WHITE

/**
 * Janggi piece movement and check legality, matching the front-end board.
 * Traditional Janggi: a player facing bikjang must separate the generals or accept it by passing.
 * Two consecutive passes or accepting bikjang end the game in a draw.
 * Repetition and point scoring are left to the game layer.
 */
class JanggiBoard(initFen: String = DEFAULT_START_FEN, keepHistory: Boolean = false) : AbstractXiangqiBoard(initFen, keepHistory) {

    override val variant: Variant = Variant.JANGGI

    private var previousPass = false
    private var drawn = false

    override fun isDraw(): Boolean =
        drawn

    override fun registerMove(move: HalfMove): PhysicalPiece? {
        val pass = move.from == move.to
        val acceptsBikjang = pass && areGeneralsFacing()
        val capture = super.registerMove(move)
        drawn = drawn || acceptsBikjang || (previousPass && pass)
        previousPass = pass
        return capture
    }

    override fun loadFen(fen: String) {
        super.loadFen(fen)
        previousPass = false
        drawn = false
    }

    override fun isIllegal(move: HalfMove, color: Color): Boolean {
        val facing = areGeneralsFacing()
        if (facing && move.from == move.to) return false
        val copy = copy()
        copy.registerMove(move)
        return (facing && copy.areGeneralsFacing()) || copy.isInCheck(color)
    }

    override fun listLegalMoves(color: Color): List<HalfMove> {
        if (isDraw()) return emptyList()
        val moves = super.listLegalMoves(color)
        if (!areGeneralsFacing() && isInCheck(color)) return moves
        val general = listAllPieces(color).single { it.abstractPieceType() == GENERAL }.position
        return moves + HalfMove(general, general)
    }

    override fun listAllMoves(pieceAtPosition: PieceAtPosition): List<HalfMove> {
        val position = pieceAtPosition.position
        val color = pieceAtPosition.color()
        require(pieceAtPosition.physicalPiece == pieceAt(position))
        val targets = when (pieceAtPosition.abstractPieceType()) {
            GENERAL, ADVISOR -> (position.getOrthogonal() + palaceDiagonalNeighbors(position))
                .filter { if (color == WHITE) it.isInRedPalace() else it.isInBlackPalace() }

            HORSE -> horseTargets(position)

            ELEPHANT -> elephantTargets(position)

            CHARIOT -> chariotTargets(position, position.getOrthogonalLines() + palaceDiagonalLines(position))

            CANNON -> cannonTargets(position)

            SOLDIER -> buildList {
                add(position.getLeft())
                add(position.getRight())
                add(if (color == WHITE) position.getTop() else position.getBottom())
                val inEnemyPalace = if (color == WHITE) position.isInBlackPalace() else position.isInRedPalace()
                if (inEnemyPalace) {
                    addAll(palaceDiagonalNeighbors(position).filter { if (color == WHITE) it.y > position.y else it.y < position.y })
                }
            }

            SUPER_CHARIOT -> throw IllegalArgumentException("Super-chariots are not Janggi pieces")
        }
        return movesToTargets(position, targets)
    }

    private fun elephantTargets(position: Position): List<Position> =
        listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0).flatMap { (dx, dy) ->
            val first = Position(position.x + dx, position.y + dy)
            if (!first.existsOnBoard() || hasPieceAt(first)) {
                emptyList()
            } else {
                val diagonals = if (dx == 0) listOf(-1 to dy, 1 to dy) else listOf(dx to -1, dx to 1)
                diagonals.mapNotNull { (diagonalX, diagonalY) ->
                    val second = Position(first.x + diagonalX, first.y + diagonalY)
                    val target = Position(second.x + diagonalX, second.y + diagonalY)
                    target.takeIf { second.existsOnBoard() && !hasPieceAt(second) && it.existsOnBoard() }
                }
            }
        }

    private fun cannonTargets(position: Position): List<Position> =
        (position.getOrthogonalLines() + palaceDiagonalLines(position)).flatMap { line ->
            val pivotIndex = line.indexOfFirst { hasPieceAt(it) }
            if (pivotIndex < 0 || pieceAt(line[pivotIndex]).pieceType.abstractPieceType == CANNON) {
                emptyList()
            } else {
                val afterPivot = line.drop(pivotIndex + 1)
                val clear = afterPivot.takeWhile { !hasPieceAt(it) }
                val capture = afterPivot.drop(clear.size).firstOrNull()
                clear + listOfNotNull(
                    capture?.takeIf {
                        containsOppositeColors(position, it) && pieceAt(it).pieceType.abstractPieceType != CANNON
                    },
                )
            }
        }

    private fun palaceCenter(position: Position): Position? =
        when {
            !position.existsOnBoard() -> null
            position.isInRedPalace() -> Position(4, 1)
            position.isInBlackPalace() -> Position(4, 8)
            else -> null
        }

    private fun palaceCorners(center: Position): List<Position> =
        center.getDiagonal()

    private fun palaceDiagonalNeighbors(position: Position): List<Position> {
        val center = palaceCenter(position) ?: return emptyList()
        return when (position) {
            center -> palaceCorners(center)
            in palaceCorners(center) -> listOf(center)
            else -> emptyList()
        }
    }

    private fun palaceDiagonalLines(position: Position): List<List<Position>> {
        val center = palaceCenter(position) ?: return emptyList()
        return when (position) {
            center -> palaceCorners(center).map { listOf(it) }
            in palaceCorners(center) -> listOf(listOf(center, Position(2 * center.x - position.x, 2 * center.y - position.y)))
            else -> emptyList()
        }
    }

    override fun copy(keepHistory: Boolean): JanggiBoard =
        copyInto(JanggiBoard(keepHistory = keepHistory), keepHistory).also {
            it.previousPass = previousPass
            it.drawn = drawn
        }

    companion object {

        /** Default setup, matching the front-end and Fairy-Stockfish fixtures; other setups can be loaded via FEN. */
        const val DEFAULT_START_FEN = "rnba1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RNBA1ABNR w - - 0 1"
    }
}
