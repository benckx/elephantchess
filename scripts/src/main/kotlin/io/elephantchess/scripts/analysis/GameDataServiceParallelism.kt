package io.elephantchess.scripts.analysis

import io.elephantchess.db.dao.codegen.Tables.BOT_GAME
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.model.AnalysisStatus
import io.elephantchess.model.AnalysisStatus.COMPLETED
import io.elephantchess.model.AnalysisStatus.NOT_STARTED
import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.servicelayer.services.GameDataService
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

/**
 * Ensure we can request multiple game analysis asynchronously without exceeding the parallelism limit
 */
object GameDataServiceParallelism : KoinScriptInit() {

    private val dslContext by inject<DSLContext>()
    private val gameDataService by inject<GameDataService>()

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val n = 20

            val botGameIds = dslContext
                .select(BOT_GAME.ID)
                .from(BOT_GAME)
                .where(BOT_GAME.ANALYSIS_STATUS.eq(NOT_STARTED))
                .and(BOT_GAME.GAME_STATUS.`in`(GameEventType.gameEndedStatuses))
                .awaitMappedRecords<String>()
                .shuffled()
                .take(n)

            println("botGameIds: ${botGameIds.joinToString(", ")}")

            botGameIds
                .map { GameId(GameType.PVB, it) }
                .forEach { gameId ->
                    println("requesting analysis for gameId $gameId")
                    val result = gameDataService.startGameAnalysis(gameId)
                    println("result: $result")
                }

            while (findAnalysisStatus(botGameIds) != listOf(COMPLETED)) {
                println("waiting for all analysis to complete")
                Thread.sleep(10.seconds.inWholeMilliseconds)
            }
        }
    }

    private suspend fun findAnalysisStatus(botGameIds: List<String>): List<AnalysisStatus> {
        return dslContext
            .select(BOT_GAME.ANALYSIS_STATUS)
            .from(BOT_GAME)
            .where(BOT_GAME.ID.`in`(botGameIds))
            .awaitMappedRecords<AnalysisStatus>()
            .toList()
            .distinct()
    }

}
