package io.elephantchess.scripts.analysis

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.model.AnalysisStatus.COMPLETED
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType.PVP
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.scripts.game.MoveAnnotationCategory
import io.elephantchess.scripts.game.calculateMoveAnnotation
import io.elephantchess.scripts.game.findAnalysisDataFromEngineBestMove
import io.elephantchess.scripts.game.moveAnnotationCategoriesInOrder
import io.elephantchess.servicelayer.services.GameDataService
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.util.Locale
import kotlin.system.exitProcess

object CountCompletedPvpMoveAnnotations : KoinScriptInit() {

    private const val PROGRESS_LOG_INTERVAL = 100

    private val dslContext by inject<DSLContext>()
    private val gameDataService by inject<GameDataService>()
    private val pvpGameDaoService by inject<PlayerVsPlayerGameDaoService>()

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            summarize()
        }
        exitProcess(0)
    }

    private suspend fun summarize() {
        val gameIds = dslContext
            .select(GAME.ID)
            .from(GAME)
            .where(GAME.ANALYSIS_STATUS.eq(COMPLETED))
            .and(GAME.VARIANT.ne(Variant.MANCHU))
            .orderBy(GAME.CREATED.asc(), GAME.ID.asc())
            .awaitMappedRecords<String>()

        println("Found ${gameIds.size} completed PvP games")

        val categoryTotals = moveAnnotationCategoriesInOrder
            .associateWith { AnnotationAggregate() }
            .toMutableMap()

        var totalMoves = 0
        var annotatedMoves = 0
        var neutralMoves = 0
        var skippedMoves = 0
        val failedGameIds = mutableListOf<String>()

        gameIds.forEachIndexed { index, gameId ->
            if (index > 0 && index % PROGRESS_LOG_INTERVAL == 0) {
                println("Processed $index / ${gameIds.size} games")
            }

            runCatching {
                processGame(gameId, categoryTotals)
            }.onSuccess { stats ->
                totalMoves += stats.totalMoves
                annotatedMoves += stats.annotatedMoves
                neutralMoves += stats.neutralMoves
                skippedMoves += stats.skippedMoves
            }.onFailure { error ->
                failedGameIds += gameId
                println("Failed to process $gameId: ${error.message}")
            }
        }

        println()
        println("Completed PvP games: ${gameIds.size}")
        println("Failed games: ${failedGameIds.size}")
        println("Processed moves: $totalMoves")
        println("Annotated moves: $annotatedMoves")
        println("Neutral moves: $neutralMoves")
        println("Skipped moves (missing analysis data): $skippedMoves")
        println()
        println("Category      Count   Avg CPL")
        moveAnnotationCategoriesInOrder.forEach { category ->
            val aggregate = categoryTotals.getValue(category)
            val avg = aggregate.averageCpl()
            val avgLabel = if (avg == null) "-" else String.format(Locale.US, "%.1f", avg)
            println("${category.name.padEnd(13)} ${aggregate.count.toString().padStart(6)}   $avgLabel")
        }

        if (failedGameIds.isNotEmpty()) {
            println()
            println("Failed game ids: ${failedGameIds.joinToString(", ")}")
        }
    }

    private suspend fun processGame(
        gameId: String,
        categoryTotals: MutableMap<MoveAnnotationCategory, AnnotationAggregate>,
    ): GameProcessingStats {
        val analysisMap = gameDataService
            .fetchAnalysisData(GameId(PVP, gameId))
            .entries
            .associateBy { it.fen }

        val board = Board(DEFAULT_START_FEN)
        var annotatedMoves = 0
        var neutralMoves = 0
        var skippedMoves = 0

        pvpGameDaoService.listMoves(gameId).forEach { move ->
            val previousNodeData = analysisMap[resetFullMoveCount(board.outputFen())]
            val engineBestAnalysis = previousNodeData?.let { findAnalysisDataFromEngineBestMove(analysisMap, it) }

            board.registerMove(move)
            val actualMoveAnalysis = analysisMap[resetFullMoveCount(board.outputFen())]

            when {
                previousNodeData == null || engineBestAnalysis == null || actualMoveAnalysis == null -> {
                    skippedMoves++
                }

                else -> {
                    val annotation = calculateMoveAnnotation(engineBestAnalysis, actualMoveAnalysis)
                    if (annotation == null) {
                        neutralMoves++
                    } else {
                        categoryTotals.getValue(annotation.category).add(annotation.cpl)
                        annotatedMoves++
                    }
                }
            }
        }

        return GameProcessingStats(
            totalMoves = annotatedMoves + neutralMoves + skippedMoves,
            annotatedMoves = annotatedMoves,
            neutralMoves = neutralMoves,
            skippedMoves = skippedMoves,
        )
    }

    private data class GameProcessingStats(
        val totalMoves: Int,
        val annotatedMoves: Int,
        val neutralMoves: Int,
        val skippedMoves: Int,
    )

    private class AnnotationAggregate {
        var count: Int = 0
            private set
        private var totalCpl: Long = 0

        fun add(cpl: Int) {
            count++
            totalCpl += cpl
        }

        fun averageCpl(): Double? =
            if (count == 0) null else totalCpl.toDouble() / count.toDouble()
    }
}
