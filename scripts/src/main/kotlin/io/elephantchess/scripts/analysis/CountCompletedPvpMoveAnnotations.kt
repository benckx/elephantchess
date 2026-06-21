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
                processGame(gameId)
            }.onSuccess { summary ->
                totalMoves += summary.totalMoves
                annotatedMoves += summary.annotatedMoves
                neutralMoves += summary.neutralMoves
                skippedMoves += summary.skippedMoves
                summary.categoryTotals.forEach { (category, aggregate) ->
                    categoryTotals[category] = categoryTotals.getValue(category).merge(aggregate)
                }
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
        println("Category      Count   Pct     Avg CPL   Min   Max")
        moveAnnotationCategoriesInOrder.forEach { category ->
            val aggregate = categoryTotals.getValue(category)
            val avg = aggregate.averageCpl()
            val avgLabel = if (avg == null) "-" else String.format(Locale.US, "%.1f", avg)
            val percentage = if (totalMoves == 0) null else aggregate.count.toDouble() * 100.0 / totalMoves.toDouble()
            val percentageLabel = if (percentage == null) "-" else String.format(Locale.US, "%.1f%%", percentage)
            val minLabel = aggregate.minCpl?.toString() ?: "-"
            val maxLabel = aggregate.maxCpl?.toString() ?: "-"
            println(
                "${category.name.padEnd(13)} " +
                    "${aggregate.count.toString().padStart(6)}   " +
                    "${percentageLabel.padStart(6)}   " +
                    "${avgLabel.padStart(7)}   " +
                    "${minLabel.padStart(4)}   " +
                    maxLabel.padStart(4),
            )
        }

        if (failedGameIds.isNotEmpty()) {
            println()
            println("Failed game ids: ${failedGameIds.joinToString(", ")}")
        }
    }

    private suspend fun processGame(
        gameId: String,
    ): MoveAnnotationSummary {
        val analysisMap = gameDataService
            .fetchAnalysisData(GameId(PVP, gameId))
            .entries
            .associateBy { it.fen }

        return summarizeMoveAnnotations(
            moves = pvpGameDaoService.listMoves(gameId),
            analysisMap = analysisMap,
        )
    }
}
