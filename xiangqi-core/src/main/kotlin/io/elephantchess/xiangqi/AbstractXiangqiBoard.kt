package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.AbstractPieceType.GENERAL
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.WHITE
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.Position.Companion.getAllPositions
import io.elephantchess.xiangqi.XiangqiBoard.Companion.HEIGHT
import io.elephantchess.xiangqi.XiangqiBoard.Companion.WIDTH

/**
 * Supports Xiangqi, Manchu and Janggi variants.
 */
abstract class AbstractXiangqiBoard(initFen: String, private val keepHistory: Boolean = false) {

    abstract val variant: Variant

    private val content: Array<Array<PhysicalPiece?>> = Array(WIDTH) { Array(HEIGHT) { null } }
    private val history = mutableListOf<HistoricalMove>()
    private var colorToPlay = WHITE
    private var fullMovesCounts = 0

    init {
        loadFen(initFen)
    }

    fun colorToPlay(): Color =
        colorToPlay

    fun isInCheck(color: Color): Boolean {
        val general = findGeneral(color) ?: error("No general for $color")
        return listAllMoves(color.reverse()).any { it.to == general.position }
    }

    fun isCheckmated(): Boolean =
        isCheckmated(WHITE) || isCheckmated(BLACK)

    fun isCheckmated(color: Color): Boolean =
        !isDraw() && color == colorToPlay && isInCheck(color) && listLegalMoves(color).isEmpty()

    fun isStalemated(): Boolean =
        isStalemated(WHITE) || isStalemated(BLACK)

    fun isStalemated(color: Color): Boolean =
        !isDraw() && color == colorToPlay && !isInCheck(color) && listLegalMoves(color).isEmpty()

    open fun isDraw(): Boolean =
        false

    abstract fun isIllegal(move: HalfMove, color: Color): Boolean

    fun areGeneralsFacing(): Boolean {
        val white = findGeneral(WHITE) ?: return false
        val black = findGeneral(BLACK) ?: return false
        return white.position.x == black.position.x &&
            (minOf(white.position.y, black.position.y) + 1 until maxOf(white.position.y, black.position.y))
                .none { hasPieceAt(Position(white.position.x, it)) }
    }

    private fun findGeneral(color: Color): PieceAtPosition? =
        listAllPieces(color).find { it.abstractPieceType() == GENERAL }

    fun calculateAttacksMap(color: Color): AttackMap {
        val copy = copy()
        copy.colorToPlay = color
        val opponentPieces = copy.listAllPieces(color.reverse())
        val legalMoves = copy.listLegalMoves(color)
        return copy.listAllPieces(color)
            .associateWith { piece ->
                val targets = legalMoves.filter { it.from == piece.position }.map { it.to }
                opponentPieces.filter { it.position in targets }
            }
            .filterValues { it.isNotEmpty() }
    }

    fun listAllPieces(color: Color): List<PieceAtPosition> =
        listAllPieces().filter { it.color() == color }

    fun listAllPieces(): List<PieceAtPosition> =
        getAllPositions().filter { hasPieceAt(it) }.map { PieceAtPosition(pieceAt(it), it) }

    fun isLegalMove(halfMove: HalfMove): Boolean =
        halfMove in listLegalMoves(colorToPlay)

    open fun listLegalMoves(color: Color): List<HalfMove> =
        listAllMoves(color).filterNot { isIllegal(it, color) }

    fun listAllMoves(color: Color): List<HalfMove> =
        listAllPieces(color).flatMap { listAllMoves(it) }

    /** Pseudo-legal moves, including moves that leave the general in check. */
    abstract fun listAllMoves(pieceAtPosition: PieceAtPosition): List<HalfMove>

    protected fun horseTargets(position: Position): List<Position> =
        buildList {
            if (position.getTop().existsOnXiangqiBoard() && !hasPieceAt(position.getTop())) {
                add(position.getTop().getTopLeft())
                add(position.getTop().getTopRight())
            }
            if (position.getBottom().existsOnXiangqiBoard() && !hasPieceAt(position.getBottom())) {
                add(position.getBottom().getBottomLeft())
                add(position.getBottom().getBottomRight())
            }
            if (position.getLeft().existsOnXiangqiBoard() && !hasPieceAt(position.getLeft())) {
                add(position.getLeft().getTopLeft())
                add(position.getLeft().getBottomLeft())
            }
            if (position.getRight().existsOnXiangqiBoard() && !hasPieceAt(position.getRight())) {
                add(position.getRight().getTopRight())
                add(position.getRight().getBottomRight())
            }
        }

    protected fun chariotTargets(position: Position, lines: List<List<Position>>): List<Position> =
        lines.flatMap { line ->
            val clear = line.takeWhile { !hasPieceAt(it) }
            clear + line.drop(clear.size).take(1).filter { containsOppositeColors(position, it) }
        }

    protected fun containsSameColors(position1: Position, position2: Position): Boolean =
        hasPieceAt(position1) && hasPieceAt(position2) && pieceAt(position1).color() == pieceAt(position2).color()

    protected fun containsOppositeColors(position1: Position, position2: Position): Boolean =
        hasPieceAt(position1) && hasPieceAt(position2) && pieceAt(position1).color() != pieceAt(position2).color()

    protected fun movesToTargets(position: Position, targets: List<Position>): List<HalfMove> =
        targets.filter { it.existsOnXiangqiBoard() && !containsSameColors(position, it) }
            .distinct()
            .map { HalfMove(position, it) }

    fun registerMove(moveAsUci: String): PhysicalPiece? {
        require(Regex(XiangqiBoard.UCI_MOVE_REGEX_PATTERN).matches(moveAsUci)) { "Move '$moveAsUci' can not be parsed" }
        return registerMove(parseMoveFromUci(moveAsUci))
    }

    open fun registerMove(move: HalfMove): PhysicalPiece? {
        val piece = pieceAt(move.from)
        check(piece.color() == colorToPlay) { "inconsistent colors" }
        val capture = if (move.from == move.to) null else pieceAtOrNull(move.to)
        content[move.from.x][move.from.y] = null
        content[move.to.x][move.to.y] = piece
        if (piece.color() == BLACK) {
            fullMovesCounts++
        }
        colorToPlay = colorToPlay.reverse()
        if (keepHistory) {
            history += HistoricalMove(
                index = history.size,
                physicalPiece = piece,
                move = move,
                capture = capture,
                isCheckingOpponent = isInCheck(piece.color().reverse()),
                attackMap = calculateAttacksMap(piece.color()),
            )
        }
        return capture
    }

    fun registerMoves(moves: Iterable<HalfMove>) {
        moves.forEach { registerMove(it) }
    }

    fun outputFen(abridged: Boolean = false): String {
        val pieces = (HEIGHT - 1 downTo 0).joinToString("/") { rank ->
            buildString {
                var empty = 0
                (0 until WIDTH).forEach { file ->
                    val piece = content[file][rank]
                    if (piece == null) {
                        empty++
                    } else {
                        if (empty > 0) append(empty)
                        empty = 0
                        append(piece.pieceType.letterNotation())
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        return if (abridged) pieces else "$pieces ${colorToPlay.toUci()} - - 0 $fullMovesCounts"
    }

    open fun loadFen(fen: String) {
        val components = fen.split(" ")
        colorToPlay = when (components[1].first()) {
            'r', 'w' -> WHITE
            'b' -> BLACK
            else -> throw IllegalArgumentException("Unrecognized color ${components[1]}")
        }
        if (components.last().all { it.isDigit() }) {
            fullMovesCounts = components.last().toInt()
        }
        val ranks = components.first().split("/")
        require(ranks.size == HEIGHT) { "FEN must contains $HEIGHT parts separated by slash characters" }
        content.forEach { it.fill(null) }
        history.clear()
        ranks.reversed().forEachIndexed { y, rank ->
            var x = 0
            rank.forEach { char ->
                if (char.isDigit()) {
                    x += char.digitToInt()
                } else {
                    content[x][y] = PhysicalPiece(PieceType.fromChar(char), Position(x, y))
                    x++
                }
            }
        }
    }

    fun hasPieceAt(position: Position): Boolean =
        position.existsOnXiangqiBoard() && content[position.x][position.y] != null

    fun pieceAt(position: Position): PhysicalPiece {
        require(position.existsOnXiangqiBoard()) { "Position $position does not exist on board" }
        return content[position.x][position.y] ?: throw IllegalArgumentException("No piece at $position")
    }

    fun pieceAtOrNull(position: Position): PhysicalPiece? =
        if (position.existsOnXiangqiBoard()) content[position.x][position.y] else null

    abstract fun copy(keepHistory: Boolean = false): AbstractXiangqiBoard

    protected fun <T : AbstractXiangqiBoard> copyInto(copy: T, keepHistory: Boolean): T {
        (0 until WIDTH).forEach { x ->
            (0 until HEIGHT).forEach { y ->
                copy.content[x][y] = content[x][y]
            }
        }
        copy.colorToPlay = colorToPlay
        copy.fullMovesCounts = fullMovesCounts
        if (keepHistory) copy.history.addAll(history.map { it.copy() })
        return copy
    }

    fun getHistory(): MovesHistory? =
        if (keepHistory) MovesHistory(history.toList()) else null

    fun print(): String {
        val separator = XiangqiBoard.PRINT_SEPARATOR
        val header = separator.repeat(3) + ('a'..'i').joinToString(separator)
        val ranks = (HEIGHT - 1 downTo 0).map { y ->
            "$y${separator.repeat(2)}" + (0 until WIDTH).joinToString(separator) { x ->
                content[x][y]?.pieceType?.letterNotation() ?: "."
            }
        }
        return (listOf(header, separator.repeat(3 + WIDTH)) + ranks).joinToString("\n")
    }

    companion object {

        fun create(
            variant: Variant,
            initFen: String = XiangqiBoard.defaultStartFen(variant),
            keepHistory: Boolean = false,
        ): AbstractXiangqiBoard =
            when (variant) {
                Variant.XIANGQI, Variant.MANCHU -> XiangqiBoard(initFen, keepHistory, variant)
                Variant.JANGGI -> JanggiBoard(initFen, keepHistory)
            }
    }
}
