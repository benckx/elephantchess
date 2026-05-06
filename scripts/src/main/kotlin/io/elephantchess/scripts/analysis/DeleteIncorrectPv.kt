package io.elephantchess.scripts.analysis

import io.elephantchess.config.ArgConfig
import io.elephantchess.config.loadAppConfig
import io.elephantchess.db.dao.codegen.Tables.ANALYSIS_ENGINE_DATA_CACHE
import io.elephantchess.db.dao.codegen.tables.pojos.AnalysisEngineDataCache
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.scripts.utils.getScriptDslContext
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.HalfMove
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine

private val appConfig = loadAppConfig(ArgConfig("prod", null))
private val dslContext = getScriptDslContext(appConfig, maximumPoolSize = 2)
private val logger = KotlinLogging.logger {}

fun main() {
    runBlocking {
        val allEntries = dslContext
            .selectFrom(ANALYSIS_ENGINE_DATA_CACHE)
            .orderBy(ANALYSIS_ENGINE_DATA_CACHE.ANALYSIS_ID, ANALYSIS_ENGINE_DATA_CACHE.ID)
            .awaitMappedRecords<AnalysisEngineDataCache>()

        logger.info { "total entries: ${allEntries.size}" }
        val incorrectIds = mutableListOf<Int>()

        allEntries.forEach { entry ->
            val board = Board(entry.fenKey)
            val nextMove = entry.pv.split(",").first()
            val isLegal = board.isLegalMove(HalfMove.parseMoveFromUci(nextMove))

            if (!isLegal) {
                logger.info { "incorrect entry [${entry.id}] ${entry.analysisId} ${entry.fenKey}" }
                incorrectIds += entry.id
            }
        }

        logger.info { "incorrect count: ${incorrectIds.size}" }

        if (incorrectIds.isNotEmpty()) {
            dslContext.transactionCoroutine { cfg ->
                DSL
                    .using(cfg)
                    .deleteFrom(ANALYSIS_ENGINE_DATA_CACHE)
                    .where(ANALYSIS_ENGINE_DATA_CACHE.ID.`in`(incorrectIds))
                    .awaitExecute()
            }
        }
    }

}
