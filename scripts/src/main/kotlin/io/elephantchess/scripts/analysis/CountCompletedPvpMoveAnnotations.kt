package io.elephantchess.scripts.analysis

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.model.AnalysisStatus.COMPLETED
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType.PVP
import io.elephantchess.model.MoveAnnotationCategory
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.servicelayer.dto.analysis.AnnotationAggregate
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.servicelayer.services.GameDataService
import io.elephantchess.servicelayer.utils.calculateMoveAnnotation
import io.elephantchess.servicelayer.utils.findAnalysisDataFromEngineBestMove
import io.elephantchess.servicelayer.utils.hasComparableAnalysisData
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import io.elephantchess.xiangqi.HalfMove.Companion.halfMoveIndexToFullMove
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.util.*
import kotlin.random.Random
import kotlin.system.exitProcess

object CountCompletedPvpMoveAnnotations : KoinScriptInit() {

    private const val ANALYSIS_HIGH_DEPTH = 20
    private const val PROGRESS_LOG_INTERVAL = 100
    private const val RANDOM_SAMPLE_SIZE_PER_CATEGORY = 4
    private const val LOCALHOST_GAME_URL_PREFIX = "http://localhost:8080/game?id="
    private const val ELEPHANTCHESS_GAME_URL_PREFIX = "https://elephantchess.io/game?id="

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
        val annotatedMoves = mutableListOf<AnnotatedMoveDetail>()
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
                annotatedMoves += summaries.annotatedMoves
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
        printRandomAnnotationSamplesSection(annotatedMoves)
        println()
        printBrilliantMovesSection(annotatedMoves.filter { it.category == MoveAnnotationCategory.BRILLIANT })

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
                actualMoveFilter = { it?.depth == ANALYSIS_HIGH_DEPTH },
            ),
            annotatedMoves = collectAnnotatedMoves(
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
        println("Skipped moves (missing or incomparable analysis data): ${totals.skippedMoves}")
        println()
        val categoryRows = MoveAnnotationCategory.entries.map { category ->
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

    private fun summarizeMoveAnnotations(
        moves: List<String>,
        analysisMap: Map<String, InfoLineResultDto>,
        startFen: String = DEFAULT_START_FEN,
        actualMoveFilter: (InfoLineResultDto?) -> Boolean = { true },
    ): MoveAnnotationSummary {
        val board = Board(startFen)
        var annotatedMoves = 0
        var neutralMoves = 0
        var skippedMoves = 0
        val categoryTotals = MoveAnnotationCategory.entries.associateWith { AnnotationAggregate() }.toMutableMap()

        moves.forEach { move ->
            val previousNodeData = analysisMap[resetFullMoveCount(board.outputFen())]
            val engineBestAnalysis = previousNodeData?.let { findAnalysisDataFromEngineBestMove(analysisMap, it) }

            board.registerMove(move)
            val actualMoveAnalysis = analysisMap[resetFullMoveCount(board.outputFen())]
            if (!actualMoveFilter(actualMoveAnalysis)) {
                return@forEach
            }

            when {
                previousNodeData == null || engineBestAnalysis == null || actualMoveAnalysis == null -> {
                    skippedMoves++
                }

                else -> {
                    if (!hasComparableAnalysisData(engineBestAnalysis, actualMoveAnalysis)) {
                        skippedMoves++
                        return@forEach
                    }

                    val annotation = calculateMoveAnnotation(engineBestAnalysis, actualMoveAnalysis)
                    if (annotation == null) {
                        neutralMoves++
                    } else {
                        categoryTotals[annotation.category] =
                            categoryTotals.getValue(annotation.category).add(annotation.cpl)
                        annotatedMoves++
                    }
                }
            }
        }

        return MoveAnnotationSummary(
            annotatedMoves = annotatedMoves,
            neutralMoves = neutralMoves,
            skippedMoves = skippedMoves,
            categoryTotals = categoryTotals,
        )
    }

    internal fun collectAnnotatedMoves(
        gameId: String,
        moves: List<String>,
        analysisMap: Map<String, InfoLineResultDto>,
        startFen: String = DEFAULT_START_FEN,
    ): List<AnnotatedMoveDetail> {
        val board = Board(startFen)
        val annotatedMoves = mutableListOf<AnnotatedMoveDetail>()

        moves.forEachIndexed { index, move ->
            val previousNodeData = analysisMap[resetFullMoveCount(board.outputFen())]
            val engineBestAnalysis = previousNodeData?.let { findAnalysisDataFromEngineBestMove(analysisMap, it) }
            val engineMove = previousNodeData?.bestMove

            board.registerMove(move)
            val actualMoveAnalysis = analysisMap[resetFullMoveCount(board.outputFen())]

            val annotation = calculateMoveAnnotation(engineBestAnalysis, actualMoveAnalysis)
            if (annotation != null &&
                previousNodeData != null &&
                engineBestAnalysis != null &&
                actualMoveAnalysis != null &&
                engineMove != null
            ) {
                annotatedMoves += AnnotatedMoveDetail(
                    category = annotation.category,
                    gameId = gameId,
                    ply = index + 1,
                    moveIndex = index,
                    fullMoveIndex = halfMoveIndexToFullMove(index),
                    fenBeforeMove = resetFullMoveCount(previousNodeData.fen),
                    playedMove = move,
                    engineMove = engineMove,
                    cpl = annotation.cpl,
                    actualMoveAnalysis = actualMoveAnalysis,
                    engineBestAnalysis = engineBestAnalysis,
                )
            }
        }

        return annotatedMoves
    }

    internal fun sampleAnnotatedMovesByCategory(
        annotatedMoves: List<AnnotatedMoveDetail>,
        sampleSize: Int = RANDOM_SAMPLE_SIZE_PER_CATEGORY,
        random: Random = Random.Default,
    ): Map<MoveAnnotationCategory, List<AnnotatedMoveDetail>> =
        MoveAnnotationCategory
            .entries
            .associateWith { category ->
                annotatedMoves
                    .filter { it.category == category }
                    .shuffled(random)
                    .take(sampleSize)
            }

    internal fun buildAnnotatedMoveLines(
        title: String,
        annotatedMoves: List<AnnotatedMoveDetail>,
    ): List<String> {
        if (annotatedMoves.isEmpty()) {
            return listOf("$title: 0")
        }

        return buildList {
            add("$title: ${annotatedMoves.size}")
            annotatedMoves.forEachIndexed { index, detail ->
                add(
                    "game=${detail.gameId} ply=${detail.ply} moveIndex=${detail.moveIndex} " +
                            "fullMoveIndex=${detail.fullMoveIndex} playedMove=${detail.playedMove} " +
                            "engineMove=${detail.engineMove} cpl=${detail.cpl} annotation=${detail.category}",
                )
                add("  fen: ${detail.fenBeforeMove}")
                add("  localhost: ${LOCALHOST_GAME_URL_PREFIX}${detail.gameId}")
                add("  elephantchess: ${ELEPHANTCHESS_GAME_URL_PREFIX}${detail.gameId}")
                add("  played: ${formatInfoLine(detail.actualMoveAnalysis)}")
                add("  engine: ${formatInfoLine(detail.engineBestAnalysis)}")
                if (index < annotatedMoves.lastIndex) {
                    add("")
                }
            }
        }
    }

    internal fun buildBrilliantMoveLines(brilliantMoves: List<AnnotatedMoveDetail>): List<String> {
        if (brilliantMoves.isEmpty()) {
            return listOf("BRILLIANT moves (all games): 0")
        }

        return buildAnnotatedMoveLines("BRILLIANT moves (all games)", brilliantMoves)
    }

    private fun printRandomAnnotationSamplesSection(annotatedMoves: List<AnnotatedMoveDetail>) {
        println("Random annotation samples (up to $RANDOM_SAMPLE_SIZE_PER_CATEGORY per category)")

        val sampledMoves = sampleAnnotatedMovesByCategory(annotatedMoves)
        val countsByCategory = annotatedMoves.groupingBy { it.category }.eachCount()

        MoveAnnotationCategory.entries.forEachIndexed { index, category ->
            val sample = sampledMoves.getValue(category)
            val total = countsByCategory[category] ?: 0
            buildAnnotatedMoveLines(
                title = "$category samples (random ${sample.size} of $total)",
                annotatedMoves = sample,
            ).forEach(::println)
            if (index < MoveAnnotationCategory.entries.lastIndex) {
                println()
            }
        }
    }

    private fun printBrilliantMovesSection(brilliantMoves: List<AnnotatedMoveDetail>) {
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
        val annotatedMoves: List<AnnotatedMoveDetail>,
    )

    internal data class AnnotatedMoveDetail(
        val category: MoveAnnotationCategory,
        val gameId: String,
        val ply: Int,
        val moveIndex: Int,
        val fullMoveIndex: Int,
        val fenBeforeMove: String,
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
        val categoryTotals: MutableMap<MoveAnnotationCategory, AnnotationAggregate> =
            MoveAnnotationCategory.entries
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

    private data class MoveAnnotationSummary(
        val annotatedMoves: Int,
        val neutralMoves: Int,
        val skippedMoves: Int,
        val categoryTotals: Map<MoveAnnotationCategory, AnnotationAggregate>,
    ) {
        val totalMoves: Int
            get() = annotatedMoves + neutralMoves + skippedMoves
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
