package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.MoveAnalysisDao
import io.elephantchess.db.dao.codegen.tables.pojos.MoveAnalysis
import io.elephantchess.db.model.MoveAnalysisDataGameEntryRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.AnalysisStatus
import io.elephantchess.model.AnalysisStatus.*
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType
import io.elephantchess.model.GameType.*
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock
import kotlin.time.Instant

class MoveAnalysisDaoService(private val dslContext: DSLContext) {

    suspend fun save(moveAnalysis: MoveAnalysis) {
        dslContext.transactionCoroutine { cfg ->
            MoveAnalysisDao(cfg).insertReactive(moveAnalysis)
        }
    }

    suspend fun listCachedAnalyzedMoves(gameId: GameId): List<String> {
        return dslContext
            .select(MOVE_ANALYSIS.FEN_KEY)
            .from(MOVE_ANALYSIS)
            .where(moveAnalysisTableIdCondition(gameId))
            .and(MOVE_ANALYSIS.ALREADY_IN_CACHE.isTrue)
            .awaitMappedRecords()
    }

    suspend fun listNonCachedAnalyzedMoves(gameId: GameId): List<MoveAnalysis> {
        return dslContext
            .selectFrom(MOVE_ANALYSIS)
            .where(moveAnalysisTableIdCondition(gameId))
            .and(MOVE_ANALYSIS.ALREADY_IN_CACHE.isFalse)
            .awaitMappedRecords()
    }

    suspend fun countAnalyzedMoves(gameId: GameId): Int {
        return dslContext
            .selectCount()
            .from(MOVE_ANALYSIS)
            .where(moveAnalysisTableIdCondition(gameId))
            .awaitSingleValue()!!
    }

    suspend fun fetchAnalysisStatus(gameId: GameId): AnalysisStatus? {
        return dslContext
            .select(gameTableAnalysisStatusField(gameId))
            .from(gameTable(gameId))
            .where(gameTableIdCondition(gameId))
            .awaitSingleValue()
    }

    suspend fun deleteAnalysisData(gameId: GameId) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .deleteFrom(MOVE_ANALYSIS)
                .where(moveAnalysisTableIdCondition(gameId))
                .awaitExecute()
        }
    }

    suspend fun resetAnalysisStatus(gameId: GameId) =
        updateAnalysisStatus(gameId, NOT_STARTED, null)

    suspend fun startAnalysis(gameId: GameId) =
        updateAnalysisStatus(gameId, STARTED, startTimeField(gameId))

    suspend fun completeAnalysis(gameId: GameId) =
        updateAnalysisStatus(gameId, COMPLETED, endTimeField(gameId))

    suspend fun cancelAnalysis(gameId: GameId) =
        updateAnalysisStatus(gameId, CANCELLED, endTimeField(gameId))

    private suspend fun updateAnalysisStatus(
        gameId: GameId,
        status: AnalysisStatus,
        timeField: Field<Instant>?,
    ) {
        dslContext.transactionCoroutine { cfg ->
            val baseUpdate = DSL
                .using(cfg)
                .update(gameTableUpdate(gameId))
                .set(gameTableAnalysisStatusField(gameId).fixed(), status)

            val update = if (timeField != null) {
                baseUpdate
                    .set(timeField.fixed(), Clock.System.now())
            } else {
                baseUpdate
                    .set(startTimeField(gameId).fixed(), null as Instant?)
                    .set(endTimeField(gameId).fixed(), null as Instant?)
            }

            update
                .where(gameTableIdCondition(gameId))
                .awaitExecute()
        }
    }

    suspend fun listLatestMoveAnalysisData(limit: Int): List<MoveAnalysisDataGameEntryRecord> {
        return dslContext
            .select(
                GAME_TYPE,
                GAME_ID,
                DSL.min(ENTRY_CREATION).`as`("min_date"),
                DSL.max(ENTRY_CREATION).`as`("max_date"),
                DSL.count().`as`("total_moves")
            )
            .from(COALESCED_VIEW)
            .groupBy(GAME_TYPE, GAME_ID)
            .orderBy(DSL.max(ENTRY_CREATION).desc())
            .limit(limit)
            .awaitRecords()
            .map { record4 ->
                MoveAnalysisDataGameEntryRecord(
                    GameId(GameType.valueOf(record4[GAME_TYPE]), record4[GAME_ID]),
                    record4["min_date", Instant::class.java],
                    record4["max_date", Instant::class.java],
                    record4["total_moves", Int::class.java]
                )
            }
    }

    suspend fun fetchLatestMoveAnalysis(): Instant? {
        return dslContext
            .select(DSL.max(MOVE_ANALYSIS.ENTRY_CREATION))
            .from(MOVE_ANALYSIS)
            .awaitSingleValue()
    }

    private companion object {

        val COALESCED_VIEW = DSL.table("move_analysis_coalesced")
        val GAME_TYPE = DSL.field("game_type", String::class.java)
        val GAME_ID = DSL.field("game_data_id", String::class.java)
        val ENTRY_CREATION = DSL.field("entry_creation", Instant::class.java)

        fun moveAnalysisTableIdCondition(gameId: GameId): Condition {
            return moveAnalysisTableIdField(gameId.type).eq(gameId.id)
        }

        fun moveAnalysisTableIdField(gameType: GameType): Field<String> {
            return when (gameType) {
                DB -> MOVE_ANALYSIS.REF_GAME_ID
                PVP -> MOVE_ANALYSIS.GAME_ID
                PVB -> MOVE_ANALYSIS.BOT_GAME_ID
            }
        }

        fun gameTableIdCondition(gameId: GameId): Condition {
            return gameTableIdField(gameId.type).eq(gameId.id)
        }

        fun gameTableIdField(gameType: GameType): Field<String> {
            return when (gameType) {
                DB -> REFERENCE_GAME.ID
                PVP -> GAME.ID
                PVB -> BOT_GAME.ID
            }
        }

        fun gameTableAnalysisStatusField(gameId: GameId) =
            gameTableAnalysisStatusField(gameId.type)

        fun gameTableAnalysisStatusField(gameType: GameType): Field<AnalysisStatus> {
            return when (gameType) {
                DB -> REFERENCE_GAME.ANALYSIS_STATUS
                PVP -> GAME.ANALYSIS_STATUS
                PVB -> BOT_GAME.ANALYSIS_STATUS
            }
        }

        fun startTimeField(gameId: GameId) =
            startTimeField(gameId.type)

        fun startTimeField(gameType: GameType): Field<Instant> {
            return when (gameType) {
                DB -> REFERENCE_GAME.ANALYSIS_START_TIME
                PVP -> GAME.ANALYSIS_START_TIME
                PVB -> BOT_GAME.ANALYSIS_START_TIME
            }
        }

        fun endTimeField(gameId: GameId) = endTimeField(gameId.type)

        fun endTimeField(gameType: GameType): Field<Instant> {
            return when (gameType) {
                DB -> REFERENCE_GAME.ANALYSIS_END_TIME
                PVP -> GAME.ANALYSIS_END_TIME
                PVB -> BOT_GAME.ANALYSIS_END_TIME
            }
        }

        fun gameTableUpdate(gameId: GameId) = gameTable(gameId).fixed()

        fun gameTable(gameId: GameId) = gameTable(gameId.type)

        fun gameTable(gameType: GameType): Table<Record> {
            return when (gameType) {
                DB -> DSL.table(REFERENCE_GAME.name, false)
                PVP -> DSL.table(GAME.name, false)
                PVB -> DSL.table(BOT_GAME.name, false)
            }
        }

    }

}
