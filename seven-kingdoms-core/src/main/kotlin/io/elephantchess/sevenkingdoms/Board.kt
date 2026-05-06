package io.elephantchess.sevenkingdoms

import io.elephantchess.sevenkingdoms.AbstractPieceType.*
import io.elephantchess.sevenkingdoms.Move.Companion.parseMoveFromUci
import io.elephantchess.sevenkingdoms.Position.Companion.BOARD_SIZE

class Board(initFen: String = DEFAULT_START_FEN) {

    private var content: Array<Array<Piece?>> = Array(BOARD_SIZE) { arrayOfNulls(BOARD_SIZE) }

    private var colorToPlay = Color.WHITE
    private val captures = mutableMapOf<Color, MutableList<PieceAtPosition>>()
    private val capturedKingdomsMap = mutableMapOf<Color, MutableList<Color>>()
    private var winner: Color? = null
    private val historicalMoves = mutableListOf<HistoricalMove>()
    private val extraEliminationEvents = mutableListOf<ExtraEliminationEvent>()

    init {
        // TODO: call "clear" and move init logic there
        //   maybe "clear" should be private and moved to here? and moved to the constructor in JS?
        loadFen(initFen)
        Color.entries.forEach { color ->
            captures[color] = mutableListOf()
            capturedKingdomsMap[color] = mutableListOf()
        }
    }

    val isGameOver
        get() = winner != null

    val currentIndex
        get() = historicalMoves.size

    fun colorToPlay(): Color = colorToPlay // TODO: should be null on game over
    fun captures(): Map<Color, List<PieceAtPosition>> = captures.toMap()
    fun capturedKingdomsMap(): Map<Color, List<Color>> = capturedKingdomsMap.toMap()
    fun capturedKingdoms(): List<Color> = capturedKingdomsMap.values.flatten().sorted()
    fun winner() = winner

    fun capturesCount(): Map<Color, Int> =
        captures.mapValues { (_, pieces) -> pieces.size }

    fun losses(): Map<Color, List<PieceAtPosition>> {
        val allCaptures = captures.values.flatten()
        val losses = mutableMapOf<Color, List<PieceAtPosition>>()
        for (capturedColor in Color.entries) {
            losses[capturedColor] = allCaptures.filter { it.color == capturedColor }
        }
        return losses
    }

    fun lossesCount(): Map<Color, Int> =
        losses().mapValues { (_, pieces) -> pieces.size }

    fun setExtraEliminationEvents(events: List<ExtraEliminationEvent>) {
        if (historicalMoves.isNotEmpty())
            throw IllegalStateException("Extra elimination events must be set before playing moves")

        val allColors = events.flatMap { it.colors }.sorted()
        if (allColors != allColors.distinct())
            throw IllegalArgumentException("Colors must be unique")

        extraEliminationEvents.clear()
        extraEliminationEvents += events
    }

    /**
     * Whether from internals factors (lost 10 pieces),
     * or from external factors (resigned or flagged)
     */
    fun allEliminatedColors(): List<Color> {
        val appliedExtraEliminationsColors =
            extraEliminationEvents
                .filter { event -> event.index <= historicalMoves.size }
                .flatMap { event -> event.colors }

        return (capturedKingdoms() + appliedExtraEliminationsColors).distinct().sorted()
    }

    fun colorsStillInGame(): List<Color> {
        val eliminated = allEliminatedColors()
        return Color.entries.filter { it !in eliminated }
    }

    fun listHistoricalMoves() =
        historicalMoves.toList()

    fun listCaptureEvents(): List<ArmyCapturedEvent> =
        historicalMoves.mapNotNull { it.armyCapturedEvent }

    fun victoryType(): VictoryType? {
        if (winner != null) {
            if (colorsStillInGame().size == 1 && colorsStillInGame().first() == winner) {
                return VictoryType.LAST_KINGDOM_REMAINING
            }

            captures[winner]?.let { pieces ->
                if (pieces.size >= CAPTURE_THRESHOLD_TO_WIN) {
                    return VictoryType.CAPTURED_ENOUGH_PIECES
                }
            }

            capturedKingdomsMap[winner]?.let { kingdoms ->
                if (kingdoms.size >= 2) {
                    return VictoryType.CAPTURED_ENOUGH_KINGDOMS
                }
            }
        }

        return null
    }

    fun loadFen(fen: String) {
        clear()
        val piecesAtPositions = parseFenToPiecesAtPositions(fen)
        for (pieceAtPosition in piecesAtPositions) {
            val position = pieceAtPosition.position
            content[position.x][position.y] = pieceAtPosition.piece
        }
    }

    fun outputFen(): String {
        val ranks = mutableListOf<String>()

        for (y in BOARD_SIZE - 1 downTo 0) {
            var spaces = 0
            var currentRank = ""
            for (x in 0 until BOARD_SIZE) {
                val piece = content[x][y]
                if (piece == null) {
                    spaces++
                } else {
                    if (spaces > 0) {
                        currentRank += spaces.toString()
                        spaces = 0
                    }
                    currentRank += piece.color.fenColorChar.lowercaseChar()
                    currentRank += piece.abstractPieceType.uci.uppercase()
                }
            }
            if (spaces > 0) {
                currentRank += spaces.toString()
            }
            ranks += currentRank
        }

        return ranks.joinToString("/")
    }

    fun listAllPieces(): List<PieceAtPosition> {
        return Position.listAll().mapNotNull { position ->
            pieceAt(position)?.let { piece ->
                PieceAtPosition(piece, position)
            }
        }
    }

    fun listPiecesByColor(color: Color): List<PieceAtPosition> {
        return listAllPieces().filter { it.color == color }
    }

    fun clear() {
        content = Array(BOARD_SIZE) { arrayOfNulls(BOARD_SIZE) }
        colorToPlay = Color.WHITE
        captures.clear()
        capturedKingdomsMap.clear()
        winner = null
        historicalMoves.clear()
    }

    private fun setPiece(pieceAtPosition: PieceAtPosition) {
        content[pieceAtPosition.position.x][pieceAtPosition.position.y] = pieceAtPosition.piece
    }

    fun pieceAt(position: Position): Piece? =
        content[position.x][position.y]

    fun hasPieceAt(position: Position) =
        pieceAt(position) != null

    fun isLegalMove(move: Move) =
        listAllLegalMovesFrom(move.from).any { it.to == move.to }

    fun registerMoves(uciMoves: List<String>) =
        uciMoves.forEach { uci -> registerMove(uci) }

    fun registerMove(uciMove: String): Piece? =
        registerMove(parseMoveFromUci(uciMove))

    fun registerMove(move: Move): Piece? {
        val piece = pieceAt(move.from)
            ?: throw IllegalArgumentException("No piece at ${move.from}")

        if (piece.color != colorToPlay)
            throw IllegalArgumentException("It's not ${piece.color}'s turn to play, it's $colorToPlay")

        if (!listCurrentLegalMoves().contains(move))
            throw IllegalArgumentException("$move is not a legal")

        val capturedPiece = pieceAt(move.to)
        content[move.from.x][move.from.y] = null
        content[move.to.x][move.to.y] = piece

        // keep history and check win conditions
        val capturedPieceAtPosition = capturedPiece?.let { PieceAtPosition(it, move.to) }
        historicalMoves += HistoricalMove(move.from, move.to, piece.color, capturedPieceAtPosition)
        if (capturedPieceAtPosition != null) {
            captures[piece.color]!! += capturedPieceAtPosition
            checkWinConditions(capturedPieceAtPosition)
        }

        updateColorToPlayOrGameOver()

        return capturedPiece
    }

    /**
     * A player is out when he loses his general or more than 10 pieces
     * The player who captures the general or the most pieces of the loser wins his remaining army
     */
    private fun checkWinConditions(capturedPiece: PieceAtPosition) {
        if (
            capturedPiece.abstractPieceType == GENERAL ||
            lossesCount()[capturedPiece.color]!! >= CAPTURE_THRESHOLD_TO_LOSE
        ) {
            convertCapturedArmy(calculateArmyCapturedEvent(capturedPiece))
        }

        // the final victory goes to the first player who wins two kingdoms or captures more than 30 pieces
        // in these cases we should only ever see max 1 candidate, but we can use the same algo as for calculateArmyCapturedEvent
        val hasCapturedEnoughKingdomsCandidates by lazy {
            capturedKingdomsMap
                .filter { (_, capturedKingdoms) -> capturedKingdoms.size >= CAPTURED_KINGDOMS_THRESHOLD_TO_WIN }
                .filterForLongestListSize()
                .map { (color, _) -> color }
        }

        val hasCapturedEnoughPiecesCandidates by lazy {
            captures
                .filter { (_, pieces) -> pieces.size >= CAPTURE_THRESHOLD_TO_WIN }
                .filterForLongestListSize()
                .map { (color, _) -> color }
        }

        if (hasCapturedEnoughKingdomsCandidates.isNotEmpty()) {
            winner = winnerTieBreak(hasCapturedEnoughKingdomsCandidates)
        } else if (hasCapturedEnoughPiecesCandidates.isNotEmpty()) {
            winner = winnerTieBreak(hasCapturedEnoughPiecesCandidates)
        }
    }

    private fun calculateArmyCapturedEvent(capturedPiece: PieceAtPosition): ArmyCapturedEvent {
        val capturedColor = capturedPiece.color

        return if (capturedPiece.abstractPieceType == GENERAL) {
            // general capture
            ArmyCapturedEvent(
                capturingColor = colorToPlay,
                capturedColor = capturedColor,
                generalCapture = true
            )
        } else {
            // capture the most pieces of the captured army
            val stillPlayingColors = colorsStillInGame()
            val candidatesColors = captures
                .filter { (capturingColor, _) -> capturingColor in stillPlayingColors }
                .filter { (capturingColor, _) -> capturingColor != capturedColor }
                .filterForLongestListSize()
                .map { (color, _) -> color }

            ArmyCapturedEvent(
                capturingColor = winnerTieBreak(candidatesColors),
                capturedColor = capturedColor,
                generalCapture = false
            )
        }
    }

    // the player who captures the general or the most pieces of the loser wins his remaining army
    // but what if e.g. multiple armies took the same number of pieces? -> tie breaking
    private fun winnerTieBreak(candidateColors: List<Color>): Color {
        require(candidateColors.isNotEmpty(), { "candidateColors is empty" })

        return if (candidateColors.size == 1) {
            candidateColors.first()
        } else {
            if (candidateColors.contains(colorToPlay)) {
                // candidate who did the capture that triggered the ArmyCapturedEvent
                colorToPlay
            } else {
                // candidate who last played before that
                historicalMoves.last { move -> candidateColors.contains(move.color) }.color
            }
        }
    }

    // the player who captures the general or the most pieces of the loser wins his remaining army
    private fun convertCapturedArmy(event: ArmyCapturedEvent) {
        val capturedPieces = listPiecesByColor(event.capturedColor)

        // keep track of captures
        captures[event.capturingColor]!! += capturedPieces

        // transfer remaining army to the capturing player
        capturedPieces
            .map { pieceAtPosition -> pieceAtPosition.copyWithColor(event.capturingColor) }
            .forEach { pieceAtPosition -> setPiece(pieceAtPosition) }

        // keep track of captured kingdoms
        capturedKingdomsMap[event.capturingColor]!! += event.capturedColor

        // add ArmyCapturedEvent to the last historical move
        historicalMoves[historicalMoves.size - 1] = historicalMoves.last().copy(armyCapturedEvent = event)
    }

    private fun updateColorToPlayOrGameOver() {
        val colorsStillInGame = colorsStillInGame()
        if (colorsStillInGame.size == 1) {
            winner = colorsStillInGame.first()
        } else {
            val index = colorsStillInGame.indexOf(colorToPlay)
            colorToPlay =
                if (index == colorsStillInGame.size - 1) {
                    colorsStillInGame.first()
                } else {
                    colorsStillInGame[index + 1]
                }
        }
    }

    /**
     * List current legal moves (for the [Color] that has to play now)
     */
    fun listCurrentLegalMoves(): List<Move> =
        if (isGameOver) {
            emptyList()
        } else {
            listLegalMovesFor(colorToPlay)
        }

    fun listLegalMovesFor(color: Color): List<Move> =
        listAllPieces()
            .filter { pieceAtPosition -> pieceAtPosition.color == color }
            .flatMap { pieceAtPosition -> listAllLegalMovesFrom(pieceAtPosition.position) }

    /**
     * Not only the ones that can currently be played given colorToPlay,
     * but all legal moves (as if turn to play was not enforced)
     */
    fun listAllLegalMoves() =
        listAllPieces().flatMap { pieceAtPosition -> listAllLegalMovesFrom(pieceAtPosition.position) }

    /**
     * Not only the ones that can currently be played given colorToPlay,
     * but all legal moves (as if turn to play was not enforced)
     */
    fun listAllLegalMovesFrom(position: Position): List<Move> {
        if (!position.isOnBoard || position.isEmperor) {
            return emptyList()
        }

        val piece = pieceAt(position) ?: return emptyList()

        var max = 0
        var movesOrthogonal = false
        var movesDiagonal = false
        var captureEnabled = true

        when (piece.abstractPieceType) {
            GENERAL -> {
                max = BOARD_SIZE
                movesOrthogonal = true
                movesDiagonal = true
            }

            CHANCELLOR -> {
                max = BOARD_SIZE
                movesOrthogonal = true
            }

            DIPLOMAT -> {
                max = BOARD_SIZE
                movesDiagonal = true
            }

            ARCHER -> {
                max = 4
                movesOrthogonal = true
                movesDiagonal = true
            }

            CROSSBOWMAN -> {
                max = 5
                movesOrthogonal = true
                movesDiagonal = true
            }

            SWORDSMAN -> {
                max = 1
                movesOrthogonal = true
            }

            DAGGER_SOLDIER -> {
                max = 1
                movesDiagonal = true
            }

            GO_BETWEEN -> {
                max = BOARD_SIZE
                movesOrthogonal = true
                movesDiagonal = true
                captureEnabled = false
            }

            else -> {}
        }

        val destinations = mutableListOf<Position>()

        when (piece.abstractPieceType) {
            GENERAL,
            CHANCELLOR,
            DIPLOMAT,
            ARCHER,
            CROSSBOWMAN,
            DAGGER_SOLDIER,
            SWORDSMAN,
            GO_BETWEEN,
                -> {
                if (movesOrthogonal) {
                    // TODO: move to Position
                    val lines = listOf(
                        position.allTopFor(max),
                        position.allBottomFor(max),
                        position.allLeftFor(max),
                        position.allRightFor(max)
                    )

                    destinations += lines.flatMap { line -> filterLineOfPositions(position, line, captureEnabled) }
                }
                if (movesDiagonal) {
                    // TODO: move to Position
                    val lines = listOf(
                        position.allTopRightDiagonalsFor(max),
                        position.allTopLeftDiagonalsFor(max),
                        position.allBottomLeftDiagonalsFor(max),
                        position.allBottomRightDiagonalsFor(max)
                    )

                    destinations += lines.flatMap { line -> filterLineOfPositions(position, line, captureEnabled) }
                }
            }

            CANNON -> {
                // TODO: move to Position
                val lines = listOf(
                    position.allTopFor(BOARD_SIZE),
                    position.allBottomFor(BOARD_SIZE),
                    position.allLeftFor(BOARD_SIZE),
                    position.allRightFor(BOARD_SIZE)
                )

                destinations += lines.flatMap { line -> filterMovesForCannon(position, line) }
            }

            KNIGHT -> {
                val top = position.top
                if (top.isOnBoard && !top.isEmperor && !hasPieceAt(top)) {
                    destinations += filterLineOfPositions(position, top.allTopLeftDiagonalsFor(3))
                    destinations += filterLineOfPositions(position, top.allTopRightDiagonalsFor(3))
                }

                val bottom = position.bottom
                if (bottom.isOnBoard && !bottom.isEmperor && !hasPieceAt(bottom)) {
                    destinations += filterLineOfPositions(position, bottom.allBottomLeftDiagonalsFor(3))
                    destinations += filterLineOfPositions(position, bottom.allBottomRightDiagonalsFor(3))
                }

                val left = position.left
                if (left.isOnBoard && !left.isEmperor && !hasPieceAt(left)) {
                    destinations += filterLineOfPositions(position, left.allTopLeftDiagonalsFor(3))
                    destinations += filterLineOfPositions(position, left.allBottomLeftDiagonalsFor(3))
                }

                val right = position.right
                if (right.isOnBoard && !right.isEmperor && !hasPieceAt(right)) {
                    destinations += filterLineOfPositions(position, right.allTopRightDiagonalsFor(3))
                    destinations += filterLineOfPositions(position, right.allBottomRightDiagonalsFor(3))
                }
            }
        }

        return destinations.map { to -> Move(position, to) }
    }

    private fun filterLineOfPositions(
        from: Position,
        positions: List<Position>,
        captureEnabled: Boolean = true,
    ): List<Position> {
        val filtered = mutableListOf<Position>()

        for (position in positions) {
            if (
                position.isEmperor ||
                !position.isOnBoard ||
                containSameColors(from, position) ||
                hasGoBetweenAt(position)
            ) {
                return filtered
            } else if (containDifferentColors(from, position)) {
                if (captureEnabled) {
                    filtered.add(position)
                }
                return filtered
            } else {
                filtered.add(position)
            }
        }

        return filtered
    }

    private fun filterMovesForCannon(from: Position, positions: List<Position>): List<Position> {
        val filtered = mutableListOf<Position>()
        var foundPivot = false

        for (position in positions) {
            if (!position.isOnBoard) {
                return filtered
            }

            if (!foundPivot) {
                // find the pivot
                if (hasPieceAt(position) || position.isEmperor) {
                    foundPivot = true
                } else {
                    filtered.add(position)
                }
            } else {
                // after the pivot has been found
                if (position.isEmperor) {
                    return filtered
                } else if (containDifferentColors(from, position)) {
                    if (!hasGoBetweenAt(position)) {
                        filtered.add(position)
                    }
                    return filtered
                } else if (containSameColors(from, position)) {
                    return filtered
                }
            }
        }

        return filtered
    }

    private fun containSameColors(p1: Position, p2: Position): Boolean {
        val piece1 = pieceAt(p1)
        val piece2 = pieceAt(p2)
        return if (piece1 == null || piece2 == null) {
            false
        } else {
            piece1.color == piece2.color
        }
    }

    private fun containDifferentColors(p1: Position, p2: Position): Boolean {
        val piece1 = pieceAt(p1)
        val piece2 = pieceAt(p2)
        return if (piece1 == null || piece2 == null) {
            false
        } else {
            piece1.color != piece2.color
        }
    }

    private fun hasGoBetweenAt(position: Position) =
        pieceAt(position)?.let { piece -> piece.abstractPieceType == GO_BETWEEN } ?: false

    fun copy(): Board {
        val boardCopy = Board()
        boardCopy.content = content.map { it.copyOf() }.toTypedArray()
        boardCopy.colorToPlay = colorToPlay
        boardCopy.captures += captures
        boardCopy.capturedKingdomsMap += capturedKingdomsMap
        boardCopy.winner = winner
        boardCopy.historicalMoves += historicalMoves.map { it.copy() }
        boardCopy.extraEliminationEvents += extraEliminationEvents.map { it.copy() }
        return boardCopy
    }

    override fun toString(): String {
        val items = mutableListOf<Pair<String, Any>>()
        if (winner == null) {
            items += Pair("colorToPlay", colorToPlay)
        } else {
            items += Pair("winner", winner!!)
            items += Pair("victoryType", victoryType()!!)
        }
        items += Pair("moves", historicalMoves.size)
        items += Pair("capturedKingdoms", capturedKingdomsMap())
        items += Pair("colorsStillInGame", colorsStillInGame())
        if (allEliminatedColors().isNotEmpty()) {
            items += Pair("eliminated", allEliminatedColors())
        }
        items += Pair("captures", captures().map { (color, pieces) -> color to pieces.size })

        return "Board{" + items.joinToString(", ") { (key, value) -> "$key=$value" } + "}"
    }

    companion object {

        const val CAPTURE_THRESHOLD_TO_WIN = 30
        const val CAPTURE_THRESHOLD_TO_LOSE = 10
        const val CAPTURED_KINGDOMS_THRESHOLD_TO_WIN = 2

        const val DEFAULT_START_FEN =
            "2dNdSdBdQdRdSdN1pNpSpBpQpRpSpN2/" +
                    "3dNdSdCdSdN3pNpSpCpSpN3/" +
                    "4dAdWdA5pApWpA3gN/" +
                    "5dH7pH3gNgS/" +
                    "5dG7pG2gAgSgB/" +
                    "14gGgHgWgCgQ/" +
                    "wN15gAgSgR/" +
                    "wSwN15gNgS/" +
                    "wRwSwA15gN/" +
                    "wQwCwWwHwG14/" +
                    "wBwSwA15bN/" +
                    "wSwN15bNbS/" +
                    "wN15bAbSbB/" +
                    "14bGbHbWbCbQ/" +
                    "5rG7oG2bAbSbR/" +
                    "5rH7oH3bNbS/" +
                    "4rArWrA5oAoWoA3bN/" +
                    "3rNrSrCrSrN3oNoSoCoSoN3/" +
                    "2rNrSrBrQrRrSrN1oNoSoBoQoRoSoN2"

    }

}
