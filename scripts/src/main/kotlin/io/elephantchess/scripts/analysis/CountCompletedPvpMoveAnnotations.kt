package io.elephantchess.scripts.analysis

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.model.AnalysisStatus.COMPLETED
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType.PVP
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.scripts.game.AnnotationAggregate
import io.elephantchess.scripts.game.MoveAnnotationCategory
import io.elephantchess.scripts.game.MoveAnnotationSummary
import io.elephantchess.scripts.game.moveAnnotationCategoriesInOrder
import io.elephantchess.scripts.game.summarizeMoveAnnotations
import io.elephantchess.servicelayer.services.GameDataService
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
            val percentageDenominator = totals.totalMoves.takeIf { it > 0 }?.toDouble()
            val percentage = percentageDenominator?.let { aggregate.count.toDouble() * 100.0 / it }
            CategoryRow(
                category = category,
                count = aggregate.count.toString(),
                percentage = if (percentage == null) "-" else String.format(Locale.US, "%.1f%%", percentage),
                average = avgLabel,
                min = aggregate.minCpl?.toString() ?: "-",
                max = aggregate.maxCpl?.toString() ?: "-",
            )
        }
        val columnWidths = categoryRows.fold(
            ColumnWidths(
                count = "Count".length,
                percentage = "Pct".length,
                average = "Avg CPL".length,
                min = "Min".length,
                max = "Max".length,
            ),
        ) { widths, row ->
            ColumnWidths(
                count = maxOf(widths.count, row.count.length),
                percentage = maxOf(widths.percentage, row.percentage.length),
                average = maxOf(widths.average, row.average.length),
                min = maxOf(widths.min, row.min.length),
                max = maxOf(widths.max, row.max.length),
            )
        }
        println(
            "Category      " +
                "Count".padStart(columnWidths.count) + "   " +
                "Pct".padStart(columnWidths.percentage) + "   " +
                "Avg CPL".padStart(columnWidths.average) + "   " +
                "Min".padStart(columnWidths.min) + "   " +
                "Max".padStart(columnWidths.max),
        )
        categoryRows.forEach { row ->
            println(
                "${row.category.name.padEnd(13)} " +
                    "${row.count.padStart(columnWidths.count)}   " +
                    "${row.percentage.padStart(columnWidths.percentage)}   " +
                    "${row.average.padStart(columnWidths.average)}   " +
                    "${row.min.padStart(columnWidths.min)}   " +
                    row.max.padStart(columnWidths.max),
            )
        }
    }

    private data class GameSummaries(
        val allMoves: MoveAnnotationSummary,
        val depth20Only: MoveAnnotationSummary,
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
        val percentage: String,
        val average: String,
        val min: String,
        val max: String,
    )

    private data class ColumnWidths(
        val count: Int,
        val percentage: Int,
        val average: Int,
        val min: Int,
        val max: Int,
    )
}
