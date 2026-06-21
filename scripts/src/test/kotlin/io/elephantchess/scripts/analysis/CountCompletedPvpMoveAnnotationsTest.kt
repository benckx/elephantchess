package io.elephantchess.scripts.analysis

import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountCompletedPvpMoveAnnotationsTest {

    @Test
    fun formatPercentagesShowsAnnotatedAndGlobalShares() {
        val percentages = CountCompletedPvpMoveAnnotations.formatPercentages(
            count = 3,
            annotatedMoves = 4,
            totalMoves = 10,
        )

        assertEquals("75.0%", percentages.annotated)
        assertEquals("30.0%", percentages.global)
    }

    @Test
    fun formatPercentagesUsesDashWhenNoDenominatorExists() {
        val percentages = CountCompletedPvpMoveAnnotations.formatPercentages(
            count = 0,
            annotatedMoves = 0,
            totalMoves = 0,
        )

        assertEquals("-", percentages.annotated)
        assertEquals("-", percentages.global)
    }

    @Test
    fun collectBrilliantMovesBuildsPlayedVersusEngineDetails() {
        val firstMove = "h2e2"
        val firstMoveBest = "c3c4"
        val secondMove = "b9c7"
        val secondMoveBest = "h9g7"

        val startBoard = Board(DEFAULT_START_FEN)
        val startFen = resetFullMoveCount(startBoard.outputFen())

        val firstMoveBestBoard = Board(DEFAULT_START_FEN)
        firstMoveBestBoard.registerMove(firstMoveBest)
        val firstMoveBestFen = resetFullMoveCount(firstMoveBestBoard.outputFen())

        val firstMoveBoard = Board(DEFAULT_START_FEN)
        firstMoveBoard.registerMove(firstMove)
        val firstMoveFen = resetFullMoveCount(firstMoveBoard.outputFen())

        val secondMoveBestBoard = Board(firstMoveBoard.outputFen())
        secondMoveBestBoard.registerMove(secondMoveBest)
        val secondMoveBestFen = resetFullMoveCount(secondMoveBestBoard.outputFen())

        val secondMoveBoard = Board(firstMoveBoard.outputFen())
        secondMoveBoard.registerMove(secondMove)
        val secondMoveFen = resetFullMoveCount(secondMoveBoard.outputFen())

        val analysisMap = mapOf(
            startFen to analysis(
                fen = startFen,
                cp = 0,
                depth = 20,
                bestMove = firstMoveBest,
                line = "info depth 20 score cp 0 pv $firstMoveBest",
            ),
            firstMoveBestFen to analysis(
                fen = firstMoveBestFen,
                cp = 180,
                depth = 20,
                line = "info depth 20 score cp 180 pv $firstMoveBest",
            ),
            firstMoveFen to analysis(
                fen = firstMoveFen,
                cp = 0,
                depth = 20,
                bestMove = secondMoveBest,
                line = "info depth 20 score cp 0 pv $secondMoveBest",
            ),
            secondMoveBestFen to analysis(
                fen = secondMoveBestFen,
                cp = 360,
                depth = 20,
                line = "info depth 20 score cp 360 pv $secondMoveBest",
            ),
            secondMoveFen to analysis(
                fen = secondMoveFen,
                cp = 0,
                depth = 18,
                line = "info depth 18 score cp 0 pv $secondMove",
            ),
        )

        val brilliantMoves = CountCompletedPvpMoveAnnotations.collectBrilliantMoves(
            gameId = "game123",
            moves = listOf(firstMove, secondMove),
            analysisMap = analysisMap,
        )

        assertEquals(1, brilliantMoves.size)
        assertEquals("game123", brilliantMoves.single().gameId)
        assertEquals(2, brilliantMoves.single().ply)
        assertEquals(secondMove, brilliantMoves.single().playedMove)
        assertEquals(secondMoveBest, brilliantMoves.single().engineMove)
        assertEquals(360, brilliantMoves.single().cpl)

        val lines = CountCompletedPvpMoveAnnotations.buildBrilliantMoveLines(brilliantMoves)
        assertEquals("BRILLIANT moves (all games): 1", lines[0])
        assertEquals("game=game123 ply=2 playedMove=b9c7 engineMove=h9g7 cpl=360", lines[1])
        assertTrue(lines[2].contains("info depth 18 score cp 0 pv b9c7"))
        assertTrue(lines[3].contains("info depth 20 score cp 360 pv h9g7"))
    }

    private fun analysis(
        fen: String,
        cp: Int,
        depth: Int,
        bestMove: String? = null,
        line: String? = null,
    ) = InfoLineResultDto(
        line = line,
        fen = fen,
        depth = depth,
        cp = cp,
        mate = null,
        pv = bestMove?.let(::listOf) ?: emptyList(),
        bestMove = bestMove,
        isCheckmate = false,
    )
}
