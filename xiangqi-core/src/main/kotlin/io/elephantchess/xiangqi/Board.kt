package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.AbstractPieceType.*
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.Position.Companion.getAllPositions
import io.github.oshai.kotlinlogging.KotlinLogging

class Board(
    initFen: String = DEFAULT_START_FEN,
    private val logMoves: Boolean = false,
    private val keepHistory: Boolean = false,
) {

    private val logger = KotlinLogging.logger {}

    private val content: Array<Array<PhysicalPiece?>> = Array(WIDTH) { Array(HEIGHT) { null } }
    private val history = mutableListOf<HistoricalMove>()
    private var colorToPlay = RED
    private var fullMovesCounts = 0

    init {
        loadFen(initFen)
    }

    fun colorToPlay(): Color = colorToPlay

    fun isInCheck(color: Color): Boolean {
        val opponentTargetPositions = listAllMoves(color.reverse()).map { move -> move.to }.distinct()
        return opponentTargetPositions.contains(findGeneral(color)!!.position)
    }

    fun isCheckmated(): Boolean {
        return isCheckmated(RED) || isCheckmated(BLACK)
    }

    fun isCheckmated(color: Color): Boolean {
        return color == colorToPlay && isInCheck(color) && allMovesAreIllegal(color)
    }

    fun isStalemated(): Boolean {
        return isStalemated(RED) || isStalemated(BLACK)
    }

    fun isStalemated(color: Color): Boolean {
        return color == colorToPlay && !isInCheck(color) && allMovesAreIllegal(color)
    }

    private fun allMovesAreIllegal(color: Color): Boolean {
        return listAllPieces(color)
            .flatMap { piece -> listAllMoves(piece) }
            .all { move -> isIllegal(move, color) }
    }

    fun isIllegal(move: HalfMove, color: Color): Boolean {
        val boardCopy = this.copy()
        boardCopy.registerMove(move)
        return boardCopy.areGeneralsFacing() || boardCopy.isInCheck(color)
    }

    private fun areGeneralsFacing(): Boolean {
        val red = findGeneral(RED)
        val black = findGeneral(BLACK)
        fun noPieceInBetweenGenerals() =
            (red!!.position.y + 1 until black!!.position.y)
                .all { y -> !hasPieceAt(Position(red.position.x, y)) }

        return red != null &&
                black != null &&
                red.position.x == black.position.x &&
                noPieceInBetweenGenerals()
    }

    private fun findGeneral(color: Color): PieceAtPosition? {
        return listAllPieces(color).find { piece -> piece.abstractPieceType() == GENERAL }
    }

    fun calculateAttacksMap(color: Color): AttackMap {
        val copy = this.copy()
        // we must force the state to be in a position where the color is actually playing
        copy.colorToPlay = color

        val allPieces = copy.listAllPieces().groupBy { it.color() }
        val playerPieces = allPieces[color]!!
        val opponentPieces = allPieces[color.reverse()]!!
        val allLegalMoves = copy.listLegalMoves(color)

        return playerPieces
            .map { piece ->
                val legalMoves =
                    allLegalMoves.filter { it.from == piece.position }
                val attackedPieces =
                    opponentPieces.filter { opponentPiece -> legalMoves.any { it.to == opponentPiece.position } }

                piece to attackedPieces
            }
            .filter { (_, attackedPieces) ->
                attackedPieces.isNotEmpty()
            }
            .toMap()
    }

    fun listAllPieces(color: Color): List<PieceAtPosition> {
        return listAllPieces().filter { piece -> piece.color() == color }
    }

    fun listAllPieces(): List<PieceAtPosition> {
        return getAllPositions()
            .filter { position -> hasPieceAt(position) }
            .map { position -> position to pieceAt(position) }
            .map { (position, piece) -> PieceAtPosition(piece, position) }
    }

    fun isLegalMove(halfMove: HalfMove): Boolean {
        return listLegalMoves(colorToPlay).contains(halfMove)
    }

    fun listLegalMoves(color: Color): List<HalfMove> {
        return listAllMoves(color).filterNot { move -> isIllegal(move, color) }
    }

    fun listAllMoves(color: Color): List<HalfMove> {
        return listAllPieces(color).flatMap { piece -> listAllMoves(piece) }
    }

    fun listAllMoves(pieceAtPosition: PieceAtPosition): List<HalfMove> {
        val piecePosition = pieceAtPosition.position
        val color = pieceAtPosition.color()
        val abstractPieceType = pieceAtPosition.abstractPieceType()

        require(pieceAtPosition.physicalPiece == pieceAt(piecePosition))

        fun filterChariotLine(line: List<Position>): List<Position> {
            val result = mutableListOf<Position>()
            line.forEach { linePosition ->
                if (!hasPieceAt(linePosition)) {
                    result += linePosition
                } else if (containsOppositeColors(linePosition, piecePosition)) {
                    result += linePosition
                    return result
                } else if (containsSameColors(linePosition, piecePosition)) {
                    return result
                }
            }
            return result
        }

        fun filterCannonLine(line: List<Position>): List<Position> {
            val result = mutableListOf<Position>()
            var foundPivot = false
            line.forEach { linePosition ->
                if (!foundPivot) {
                    if (!hasPieceAt(linePosition)) {
                        result += linePosition
                    } else {
                        foundPivot = true
                    }
                } else {
                    if (containsOppositeColors(piecePosition, linePosition)) {
                        result += linePosition
                        return result
                    } else if (containsSameColors(piecePosition, linePosition)) {
                        return result
                    }
                }
            }
            return result
        }

        fun filterInItsPalace(positions: List<Position>): List<Position> {
            return when (color) {
                RED -> positions.filter { it.isInRedPalace() }
                BLACK -> positions.filter { it.isInBlackPalace() }
            }
        }

        fun hasCrossedTheRiver(): Boolean {
            return when (color) {
                RED -> piecePosition.y >= 5
                BLACK -> piecePosition.y <= 4
            }
        }

        fun isAcrossTheRiver(position: Position): Boolean {
            return when (color) {
                RED -> position.y >= 5
                BLACK -> position.y <= 4
            }
        }

        val basePositions: List<Position> =
            when (abstractPieceType) {
                CHARIOT -> {
                    piecePosition
                        .getOrthogonalLines()
                        .flatMap { line -> filterChariotLine(line) }
                }

                CANNON -> {
                    piecePosition
                        .getOrthogonalLines()
                        .flatMap { line -> filterCannonLine(line) }
                }

                HORSE -> {
                    val result = mutableListOf<Position>()
                    val top = piecePosition.getTop()
                    val bottom = piecePosition.getBottom()
                    val left = piecePosition.getLeft()
                    val right = piecePosition.getRight()
                    if (top.existsOnBoard() && !hasPieceAt(top)) {
                        result += top.getTopLeft()
                        result += top.getTopRight()
                    }
                    if (bottom.existsOnBoard() && !hasPieceAt(bottom)) {
                        result += bottom.getBottomLeft()
                        result += bottom.getBottomRight()
                    }
                    if (left.existsOnBoard() && !hasPieceAt(left)) {
                        result += left.getTopLeft()
                        result += left.getBottomLeft()
                    }
                    if (right.existsOnBoard() && !hasPieceAt(right)) {
                        result += right.getTopRight()
                        result += right.getBottomRight()
                    }
                    result
                }

                ELEPHANT -> {
                    val result = mutableListOf<Position>()
                    if (!hasPieceAt(piecePosition.getTopLeft())) {
                        result += piecePosition.getTopLeft().getTopLeft()
                    }
                    if (!hasPieceAt(piecePosition.getTopRight())) {
                        result += piecePosition.getTopRight().getTopRight()
                    }
                    if (!hasPieceAt(piecePosition.getBottomLeft())) {
                        result += piecePosition.getBottomLeft().getBottomLeft()
                    }
                    if (!hasPieceAt(piecePosition.getBottomRight())) {
                        result += piecePosition.getBottomRight().getBottomRight()
                    }
                    result.filterNot { position -> isAcrossTheRiver(position) }
                }

                SOLDIER -> {
                    val result = mutableListOf<Position>()
                    result += when (color) {
                        RED -> piecePosition.getTop()
                        BLACK -> piecePosition.getBottom()
                    }
                    if (hasCrossedTheRiver()) {
                        result += listOf(piecePosition.getLeft(), piecePosition.getRight())
                    }
                    result
                }

                GENERAL -> filterInItsPalace(piecePosition.getOrthogonal())
                ADVISOR -> filterInItsPalace(piecePosition.getDiagonal())
            }

        return basePositions
            .filter { position -> position.existsOnBoard() && !containsSameColors(piecePosition, position) }
            .map { position -> HalfMove(piecePosition, position) }
    }

    private fun containsSameColors(position1: Position, position2: Position): Boolean {
        return hasPieceAt(position1) && hasPieceAt(position2) && pieceAt(position1).color() == pieceAt(position2).color()
    }

    private fun containsOppositeColors(position1: Position, position2: Position): Boolean {
        return hasPieceAt(position1) && hasPieceAt(position2) && pieceAt(position1).color() != pieceAt(position2).color()
    }

    fun registerMove(moveAsUci: String): PhysicalPiece? {
        return if (Regex(UCI_MOVE_REGEX_PATTERN).matches(moveAsUci)) {
            registerMove(parseMoveFromUci(moveAsUci))
        } else {
            throw IllegalArgumentException("Move '$moveAsUci' can not be parsed")
        }
    }

    fun registerMove(move: HalfMove): PhysicalPiece? {
        fun logMoveIfEnabled(physicalPiece: PhysicalPiece, capture: PhysicalPiece?) {
            if (logMoves && logger.isDebugEnabled()) {
                val beforeLines = printToLines()

                logger.debug {
                    val base = "${physicalPiece.pieceType.prettyName()} moves $move"
                    if (capture == null) {
                        base
                    } else {
                        base + " and takes ${capture.pieceType.prettyName()}"
                    }
                }
                logger.debug {
                    val afterLines = printToLines()
                    require(afterLines.size == beforeLines.size)
                    val nbrOfLines = afterLines.size
                    val mergedLines = (0 until nbrOfLines).map { i ->
                        beforeLines[i] + " ".repeat(3) + afterLines[i]
                    }
                    "\n\n" + mergedLines.joinToString("\n")
                }
            }
        }

        val piece = pieceAt(move.from)
        if (piece.color() != colorToPlay) {
            throw IllegalStateException("inconsistent colors")
        }
        val capture = movePiece(move, piece)
        if (piece.color() == BLACK) {
            fullMovesCounts++
        }
        colorToPlay = colorToPlay.reverse()
        if (keepHistory) {
            val color = piece.color()
            val opponentColor = color.reverse()

            history += HistoricalMove(
                index = history.size,
                physicalPiece = piece,
                move = move,
                capture = capture,
                isCheckingOpponent = isInCheck(opponentColor),
                attackMap = calculateAttacksMap(color)
            )
        }
        logMoveIfEnabled(piece, capture)
        return capture
    }

    fun registerMoves(moves: Iterable<HalfMove>) {
        moves.forEach { move -> registerMove(move) }
    }

    fun outputFen(abridged: Boolean = false): String {
        val firstPart = (HEIGHT - 1 downTo 0).joinToString("/") { rank -> rankToFen(rank) }
        val secondPart = "${colorToPlay.toUci()} - - 0 $fullMovesCounts"
        return if (abridged) {
            firstPart
        } else {
            "$firstPart $secondPart"
        }
    }

    private fun rankToFen(rank: Int): String {
        fun extractRankPieces(rank: Int) =
            (0 until WIDTH).map { x -> content[x][rank] }

        var count = 0
        var result = ""
        extractRankPieces(rank).forEach { piece ->
            if (piece == null) {
                count++
            } else {
                if (count > 0) {
                    result += count.toString()
                    count = 0
                }
                result += piece.pieceType.letterNotation()
            }
        }
        if (count > 0) {
            result += count.toString()
        }
        return result
    }

    // TODO: update "full moves count" + "color to play" accordingly
    fun loadFen(fen: String) {
        fun loadFenLine(fenLine: String, y: Int) {
            var x = 0
            fenLine.forEach { char ->
                if (char.isDigit()) {
                    x += char.digitToInt()
                } else {
                    val pieceType = PieceType.fromChar(char)
                    content[x][y] = PhysicalPiece(pieceType, Position(x, y))
                    x += 1
                }
            }
        }

        val fenComponents = fen.split(" ")

        // color to play
        colorToPlay =
            when (fenComponents[1].toCharArray().first()) {
                'r' -> RED
                'w' -> RED
                'b' -> BLACK
                else -> throw IllegalArgumentException("Unrecognized color ${fenComponents[1]}")
            }

        // full moves
        if (fenComponents.last().all { c -> c.isDigit() }) {
            fullMovesCounts = fenComponents.last().toInt()
        }

        // load pieces info
        val fenLines = fenComponents.first().split("/")
        if (fenLines.size != HEIGHT) {
            throw IllegalArgumentException("FEN must contains $HEIGHT parts separated by slash characters")
        }
        clear()
        (0 until HEIGHT).forEach { y ->
            // from top to bottom, i.e. from HEIGHT to 0
            loadFenLine(fenLines[HEIGHT - 1 - y], y)
        }
    }

    fun hasPieceAt(position: Position): Boolean {
        return position.existsOnBoard() && content[position.x][position.y] != null
    }

    fun pieceAt(position: Position): PhysicalPiece {
        if (!position.existsOnBoard()) {
            throw IllegalArgumentException("Position $position does not exist on board")
        }

        return content[position.x][position.y] ?: throw IllegalArgumentException("No piece at $position")
    }

    fun pieceAtOrNull(position: Position): PhysicalPiece? {
        return if (position.existsOnBoard()) {
            content[position.x][position.y]
        } else {
            null
        }
    }

    private fun movePiece(move: HalfMove, physicalPiece: PhysicalPiece): PhysicalPiece? {
        val capture = pieceAtOrNull(move.to)
        content[move.from.x][move.from.y] = null
        content[move.to.x][move.to.y] = physicalPiece
        return capture
    }

    fun copy(keepHistory: Boolean = false): Board {
        val copy = Board(keepHistory = keepHistory)
        copy.clear()
        (0 until WIDTH).forEach { x ->
            (0 until HEIGHT).forEach { y ->
                copy.content[x][y] = this.content[x][y]
            }
        }
        copy.colorToPlay = this.colorToPlay
        copy.fullMovesCounts = this.fullMovesCounts
        if (keepHistory) {
            copy.history.addAll(this.history.toList().map { it.copy() })
        }
        return copy
    }

    private fun clear() {
        (0 until WIDTH).forEach { x ->
            (0 until HEIGHT).forEach { y ->
                content[x][y] = null
            }
        }

        this.history.clear()
    }

    fun getHistory(): MovesHistory? {
        return if (keepHistory) {
            MovesHistory(history.toList())
        } else {
            null
        }
    }

    fun print(): String {
        return printToLines().joinToString("\n")
    }

    private fun printToLines(): List<String> {
        fun addFilesIndexes(lines: List<String>): List<String> {
            val indexLines = PRINT_SEPARATOR.repeat(3) + ('a'..'i').joinToString(PRINT_SEPARATOR)
            val emptyLine = PRINT_SEPARATOR.repeat(3 + WIDTH)
            return listOf(indexLines, emptyLine) + lines
        }

        fun addRanksIndexes(lines: List<String>): List<String> {
            return lines.mapIndexed { i, line -> "${HEIGHT - 1 - i}${PRINT_SEPARATOR.repeat(2)}$line" }
        }

        fun printBoardToLines(): List<String> {
            return (HEIGHT - 1 downTo 0).map { y ->
                (0 until WIDTH).joinToString(PRINT_SEPARATOR) { x ->
                    content[x][y]?.pieceType?.letterNotation() ?: "."
                }
            }
        }

        return addFilesIndexes(addRanksIndexes(printBoardToLines()))
    }

    companion object {

        private val logger = KotlinLogging.logger {}

        const val WIDTH = 9
        const val HEIGHT = 10
        const val DEFAULT_START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0"
        const val PRINT_SEPARATOR = " "
        const val UCI_MOVE_REGEX_PATTERN = "[a-i][0-9][a-i][0-9]"

        fun validateFen(fen: String) {
            try {
                Board(fen)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid FEN: $fen: ${e.message}")
            }
        }

        fun colorToPlay(fen: String): Color {
            val board = Board(fen)
            return board.colorToPlay()
        }

        fun isMoveLegal(currentFen: String, move: String): Boolean {
            return try {
                val board = Board(currentFen)
                board.isLegalMove(parseMoveFromUci(move))
            } catch (e: Exception) {
                logger.error { "Error while checking if move is legal $e" }
                false
            }
        }

        fun calculateNewFen(oldFen: String, move: String): String {
            val board = Board(oldFen)
            board.registerMove(move)
            return board.outputFen()
        }

        fun isCheckmated(fen: String): Boolean {
            val board = Board(fen)
            val colorToPlay = board.colorToPlay()
            return board.isCheckmated(colorToPlay)
        }

        fun isStalemated(fen: String): Boolean {
            val board = Board(fen)
            val colorToPlay = board.colorToPlay()
            return board.isStalemated(colorToPlay)
        }

        fun isInCheck(fen: String, color: Color): Boolean {
            val board = Board(fen)
            return board.isInCheck(color)
        }

        fun moveToFens(
            movesHistory: List<String>,
            startFen: String = DEFAULT_START_FEN,
            abridged: Boolean = false,
        ): List<String> {
            val board = Board(startFen)
            val fens = mutableListOf<String>()
            fens += board.outputFen(abridged)
            movesHistory.forEach { move ->
                board.registerMove(move)
                fens += board.outputFen(abridged)
            }
            return fens.toList()
        }

        /**
         * Set 0 at the end of a FEN
         */
        fun resetFullMoveCount(fen: String): String {
            return fen.split(" ").dropLast(1).joinToString(" ") + " 0"
        }

    }

}
