package io.elephantchess.scripts.analysis

import io.elephantchess.servicelayer.analysis.calculateMoveAnnotation
import io.elephantchess.servicelayer.analysis.MoveAnnotationCategory
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun collectAnnotatedMovesBuildsPlayedVersusEngineDetails() {
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
                depth = 20,
                line = "info depth 20 score cp 0 pv $secondMove",
            ),
        )

        val annotatedMoves = CountCompletedPvpMoveAnnotations.collectAnnotatedMoves(
            gameId = "game123",
            moves = listOf(firstMove, secondMove),
            analysisMap = analysisMap,
        )

        assertEquals(2, annotatedMoves.size)
        val brilliantMove = annotatedMoves.single { it.category == MoveAnnotationCategory.BRILLIANT }
        assertEquals("game123", brilliantMove.gameId)
        assertEquals(2, brilliantMove.ply)
        assertEquals(1, brilliantMove.moveIndex)
        assertEquals(1, brilliantMove.fullMoveIndex)
        assertEquals(firstMoveFen, brilliantMove.fenBeforeMove)
        assertEquals(secondMove, brilliantMove.playedMove)
        assertEquals(secondMoveBest, brilliantMove.engineMove)
        assertEquals(360, brilliantMove.cpl)

        val lines = CountCompletedPvpMoveAnnotations.buildBrilliantMoveLines(listOf(brilliantMove))
        assertEquals("BRILLIANT moves (all games): 1", lines[0])
        assertEquals(
            "game=game123 ply=2 moveIndex=1 fullMoveIndex=1 playedMove=b9c7 engineMove=h9g7 cpl=360 annotation=BRILLIANT",
            lines[1],
        )
        assertEquals("  fen: $firstMoveFen", lines[2])
        assertEquals("  localhost: http://localhost:8080/game?id=game123", lines[3])
        assertEquals("  elephantchess: https://elephantchess.io/game?id=game123", lines[4])
        assertTrue(lines[5].contains("info depth 20 score cp 0 pv b9c7"))
        assertTrue(lines[6].contains("info depth 20 score cp 360 pv h9g7"))
    }

    @Test
    fun sampleAnnotatedMovesByCategoryLimitsEachCategoryIndependently() {
        val categories = listOf(
            MoveAnnotationCategory.BLUNDER,
            MoveAnnotationCategory.BLUNDER,
            MoveAnnotationCategory.BLUNDER,
            MoveAnnotationCategory.BLUNDER,
            MoveAnnotationCategory.BLUNDER,
            MoveAnnotationCategory.BLUNDER,
            MoveAnnotationCategory.GOOD,
            MoveAnnotationCategory.GOOD,
        )
        val annotatedMoves = categories.mapIndexed { index, category ->
            CountCompletedPvpMoveAnnotations.AnnotatedMoveDetail(
                category = category,
                gameId = "game$index",
                ply = index + 1,
                moveIndex = index,
                fullMoveIndex = (index / 2) + 1,
                fenBeforeMove = "fen$index",
                playedMove = "a0a1",
                engineMove = "a0a2",
                cpl = 100 + index,
                actualMoveAnalysis = analysis("actual$index", cp = 0, depth = 20),
                engineBestAnalysis = analysis("engine$index", cp = 100, depth = 20),
            )
        }

        val samples = CountCompletedPvpMoveAnnotations.sampleAnnotatedMovesByCategory(
            annotatedMoves = annotatedMoves,
            sampleSize = 5,
            random = Random(1234),
        )

        assertEquals(5, samples.getValue(MoveAnnotationCategory.BLUNDER).size)
        assertEquals(2, samples.getValue(MoveAnnotationCategory.GOOD).size)
        assertTrue(samples.getValue(MoveAnnotationCategory.BLUNDER).all { it.category == MoveAnnotationCategory.BLUNDER })
        assertTrue(samples.getValue(MoveAnnotationCategory.GOOD).all { it.category == MoveAnnotationCategory.GOOD })
        assertEquals(
            listOf("game2", "game5", "game4", "game3", "game0"),
            samples.getValue(MoveAnnotationCategory.BLUNDER).map { it.gameId },
        )
        assertEquals(
            listOf("game7", "game6"),
            samples.getValue(MoveAnnotationCategory.GOOD).map { it.gameId },
        )
        assertEquals(0, samples.getValue(MoveAnnotationCategory.BRILLIANT).size)
    }

    @Test
    fun collectBrilliantMovesSkipsIncomparableDepths() {
        val actualMove = analysis(
            fen = "actual",
            cp = 0,
            depth = 20,
            line = "info depth 20 score cp 0",
        )
        val engineMove = analysis(
            fen = "engine",
            cp = 360,
            depth = 18,
            line = "info depth 18 score cp 360",
        )

        assertNull(calculateMoveAnnotation(engineMove, actualMove))
    }

    @Test
    fun calculateMoveAnnotationSkipsDepthZeroMateEngineLine() {
        assertNull(
            calculateMoveAnnotation(
                engineBest = analysis(
                    fen = "engine",
                    cp = null,
                    mate = 0,
                    depth = 0,
                    line = "info depth 0 score mate 0",
                ),
                actualMove = analysis(
                    fen = "actual",
                    cp = -4155,
                    depth = 20,
                    line = "info depth 20 score cp -4155",
                ),
            ),
        )
    }

    private fun analysis(
        fen: String,
        cp: Int?,
        depth: Int,
        mate: Int? = null,
        bestMove: String? = null,
        line: String? = null,
    ) = InfoLineResultDto(
        line = line,
        fen = fen,
        depth = depth,
        cp = cp,
        mate = mate,
        pv = bestMove?.let(::listOf) ?: emptyList(),
        bestMove = bestMove,
        isCheckmate = false,
    )
}
