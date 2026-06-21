package io.elephantchess.scripts.analysis

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.model.AnalysisStatus.COMPLETED
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType.PVP
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.scripts.game.AnnotationAggregate
import io.elephantchess.scripts.game.calculateMoveAnnotation
import io.elephantchess.scripts.game.findAnalysisDataFromEngineBestMove
import io.elephantchess.scripts.game.MoveAnnotationCategory
import io.elephantchess.scripts.game.MoveAnnotationSummary
import io.elephantchess.scripts.game.moveAnnotationCategoriesInOrder
import io.elephantchess.scripts.game.summarizeMoveAnnotations
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
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

    private const val ANALYSIS_DEPTH_20 = 20
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

        val overallStats = SummaryTotals()
        val depth20Stats = SummaryTotals()
        val brilliantMoves = mutableListOf<BrilliantMoveDetail>()
        val failedGameIds = mutableListOf<String>()

        gameIds.forEachIndexed { index, gameId ->
            if (index > 0 && index % PROGRESS_LOG_INTERVAL == 0) {
                println("Processed $index / ${gameIds.size} games")
            }

            runCatching {
                processGame(gameId)
            }.onSuccess { summaries ->
                overallStats.merge(summaries.allMoves)
                depth20Stats.merge(summaries.depth20Only)
                brilliantMoves += summaries.brilliantMoves
            }.onFailure { error ->
                failedGameIds += gameId
                println("Failed to process $gameId: ${error.message}")
            }
        }

        println()
        printSummarySection(
            title = "All completed PvP move annotations",
            gameCount = gameIds.size,
            failedCount = failedGameIds.size,
            totals = overallStats,
        )
        println()
        printSummarySection(
            title = "Completed PvP move annotations (actual move depth 20 only)",
            gameCount = gameIds.size,
            failedCount = failedGameIds.size,
            totals = depth20Stats,
        )
        println()
        printBrilliantMovesSection(brilliantMoves)

        if (failedGameIds.isNotEmpty()) {
            println()
            println("Failed game ids: ${failedGameIds.joinToString(", ")}")
        }
    }

    private suspend fun processGame(
        gameId: String,
    ): GameSummaries {
        val analysisMap = gameDataService
            .fetchAnalysisData(GameId(PVP, gameId))
            .entries
            .associateBy { it.fen }

        val moves = pvpGameDaoService.listMoves(gameId)
        return GameSummaries(
            allMoves = summarizeMoveAnnotations(
                moves = moves,
                analysisMap = analysisMap,
            ),
            depth20Only = summarizeMoveAnnotations(
                moves = moves,
                analysisMap = analysisMap,
                actualMoveFilter = { it?.depth == ANALYSIS_DEPTH_20 },
            ),
            brilliantMoves = collectBrilliantMoves(
                gameId = gameId,
                moves = moves,
                analysisMap = analysisMap,
            ),
        )
    }

    private fun printSummarySection(
        title: String,
        gameCount: Int,
        failedCount: Int,
        totals: SummaryTotals,
    ) {
        println(title)
        println("Completed PvP games: $gameCount")
        println("Failed games: $failedCount")
        println("Processed moves: ${totals.totalMoves}")
        println("Annotated moves: ${totals.annotatedMoves}")
        println("Neutral moves: ${totals.neutralMoves}")
        println("Skipped moves (missing analysis data): ${totals.skippedMoves}")
        println()
        val categoryRows = moveAnnotationCategoriesInOrder.map { category ->
            val aggregate = totals.categoryTotals.getValue(category)
            val avg = aggregate.averageCpl()
            val avgLabel = if (avg == null) "-" else String.format(Locale.US, "%.1f", avg)
            val percentages = formatPercentages(
                count = aggregate.count,
                annotatedMoves = totals.annotatedMoves,
                totalMoves = totals.totalMoves,
            )
            CategoryRow(
                category = category,
                count = aggregate.count.toString(),
                annotatedPercentage = percentages.annotated,
                globalPercentage = percentages.global,
                average = avgLabel,
                min = aggregate.minCpl?.toString() ?: "-",
                max = aggregate.maxCpl?.toString() ?: "-",
            )
        }
        val columnWidths = categoryRows.fold(
            ColumnWidths(
                count = "Count".length,
                annotatedPercentage = "Pct Ann".length,
                globalPercentage = "Pct Global".length,
                average = "Avg CPL".length,
                min = "Min".length,
                max = "Max".length,
            ),
        ) { widths, row ->
            ColumnWidths(
                count = maxOf(widths.count, row.count.length),
                annotatedPercentage = maxOf(widths.annotatedPercentage, row.annotatedPercentage.length),
                globalPercentage = maxOf(widths.globalPercentage, row.globalPercentage.length),
                average = maxOf(widths.average, row.average.length),
                min = maxOf(widths.min, row.min.length),
                max = maxOf(widths.max, row.max.length),
            )
        }
        println(
            "Category      " +
                "Count".padStart(columnWidths.count) + "   " +
                "Pct Ann".padStart(columnWidths.annotatedPercentage) + "   " +
                "Pct Global".padStart(columnWidths.globalPercentage) + "   " +
                "Avg CPL".padStart(columnWidths.average) + "   " +
                "Min".padStart(columnWidths.min) + "   " +
                "Max".padStart(columnWidths.max),
        )
        categoryRows.forEach { row ->
            println(
                "${row.category.name.padEnd(13)} " +
                    "${row.count.padStart(columnWidths.count)}   " +
                    "${row.annotatedPercentage.padStart(columnWidths.annotatedPercentage)}   " +
                    "${row.globalPercentage.padStart(columnWidths.globalPercentage)}   " +
                    "${row.average.padStart(columnWidths.average)}   " +
                    "${row.min.padStart(columnWidths.min)}   " +
                    row.max.padStart(columnWidths.max),
            )
        }
    }

    internal fun formatPercentages(
        count: Int,
        annotatedMoves: Int,
        totalMoves: Int,
    ) = AnnotationPercentages(
        annotated = formatPercentage(count, annotatedMoves),
        global = formatPercentage(count, totalMoves),
    )

    private fun formatPercentage(count: Int, denominator: Int): String =
        denominator
            .takeIf { it > 0 }
            ?.let { String.format(Locale.US, "%.1f%%", count.toDouble() * 100.0 / it) }
            ?: "-"

    internal fun collectBrilliantMoves(
        gameId: String,
        moves: List<String>,
        analysisMap: Map<String, InfoLineResultDto>,
        startFen: String = DEFAULT_START_FEN,
    ): List<BrilliantMoveDetail> {
        val board = Board(startFen)
        val brilliantMoves = mutableListOf<BrilliantMoveDetail>()

        moves.forEachIndexed { index, move ->
            val previousNodeData = analysisMap[resetFullMoveCount(board.outputFen())]
            val engineBestAnalysis = previousNodeData?.let { findAnalysisDataFromEngineBestMove(analysisMap, it) }
            val engineMove = previousNodeData?.bestMove

            board.registerMove(move)
            val actualMoveAnalysis = analysisMap[resetFullMoveCount(board.outputFen())]

            val annotation = calculateMoveAnnotation(engineBestAnalysis, actualMoveAnalysis)
            if (annotation?.category == MoveAnnotationCategory.BRILLIANT &&
                engineBestAnalysis != null &&
                actualMoveAnalysis != null &&
                engineMove != null
            ) {
                brilliantMoves += BrilliantMoveDetail(
                    gameId = gameId,
                    ply = index + 1,
                    playedMove = move,
                    engineMove = engineMove,
                    cpl = annotation.cpl,
                    actualMoveAnalysis = actualMoveAnalysis,
                    engineBestAnalysis = engineBestAnalysis,
                )
            }
        }

        return brilliantMoves
    }

    internal fun buildBrilliantMoveLines(brilliantMoves: List<BrilliantMoveDetail>): List<String> {
        if (brilliantMoves.isEmpty()) {
            return listOf("BRILLIANT moves (all games): 0")
        }

        return buildList {
            add("BRILLIANT moves (all games): ${brilliantMoves.size}")
            brilliantMoves.forEachIndexed { index, detail ->
                add("game=${detail.gameId} ply=${detail.ply} playedMove=${detail.playedMove} engineMove=${detail.engineMove} cpl=${detail.cpl}")
                add("  played: ${formatInfoLine(detail.actualMoveAnalysis)}")
                add("  engine: ${formatInfoLine(detail.engineBestAnalysis)}")
                if (index < brilliantMoves.lastIndex) {
                    add("")
                }
            }
        }
    }

    private fun printBrilliantMovesSection(brilliantMoves: List<BrilliantMoveDetail>) {
        buildBrilliantMoveLines(brilliantMoves).forEach(::println)
    }

    private fun formatInfoLine(infoLine: InfoLineResultDto): String =
        infoLine.line ?: buildString {
            append("depth=")
            append(infoLine.depth ?: "-")
            append(" cp=")
            append(infoLine.cp ?: "-")
            append(" mate=")
            append(infoLine.mate ?: "-")
            append(" bestMove=")
            append(infoLine.bestMove ?: "-")
            append(" pv=")
            append(
                if (infoLine.pv.isEmpty()) {
                    "-"
                } else {
                    infoLine.pv.joinToString(" ")
                },
            )
        }

    private data class GameSummaries(
        val allMoves: MoveAnnotationSummary,
        val depth20Only: MoveAnnotationSummary,
        val brilliantMoves: List<BrilliantMoveDetail>,
    )

    internal data class BrilliantMoveDetail(
        val gameId: String,
        val ply: Int,
        val playedMove: String,
        val engineMove: String,
        val cpl: Int,
        val actualMoveAnalysis: InfoLineResultDto,
        val engineBestAnalysis: InfoLineResultDto,
    )

    private data class SummaryTotals(
        var totalMoves: Int = 0,
        var annotatedMoves: Int = 0,
        var neutralMoves: Int = 0,
        var skippedMoves: Int = 0,
        val categoryTotals: MutableMap<MoveAnnotationCategory, AnnotationAggregate> = moveAnnotationCategoriesInOrder
            .associateWith { AnnotationAggregate() }
            .toMutableMap(),
    ) {
        fun merge(summary: MoveAnnotationSummary) {
            totalMoves += summary.totalMoves
            annotatedMoves += summary.annotatedMoves
            neutralMoves += summary.neutralMoves
            skippedMoves += summary.skippedMoves
            summary.categoryTotals.forEach { (category, aggregate) ->
                categoryTotals[category] = categoryTotals.getValue(category).merge(aggregate)
            }
        }
    }

    private data class CategoryRow(
        val category: MoveAnnotationCategory,
        val count: String,
        val annotatedPercentage: String,
        val globalPercentage: String,
        val average: String,
        val min: String,
        val max: String,
    )

    internal data class AnnotationPercentages(
        val annotated: String,
        val global: String,
    )

    private data class ColumnWidths(
        val count: Int,
        val annotatedPercentage: Int,
        val globalPercentage: Int,
        val average: Int,
        val min: Int,
        val max: Int,
    )
}
