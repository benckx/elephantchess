package io.elephantchess.db.services

import io.elephantchess.db.codegen.OffsetDateTimeInstantConverter
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
import org.jooq.impl.SQLDataType
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

    suspend fun resetAnalysisStatus(gameId: GameId) {
        updateAnalysisStatus(gameId, NOT_STARTED, null)
        setAnalyzedFromBatch(gameId, false)
    }

    suspend fun startAnalysis(gameId: GameId, isFromBatch: Boolean) {
        updateAnalysisStatus(gameId, STARTED, startTimeField(gameId))
        if (isFromBatch) {
            setAnalyzedFromBatch(gameId, true)
        }
    }

    private suspend fun setAnalyzedFromBatch(gameId: GameId, value: Boolean) {
        dslContext.transactionCoroutine { cfg ->
            when (gameId.type) {
                DB -> DSL
                    .using(cfg)
                    .update(REFERENCE_GAME.fixed())
                    .set(REFERENCE_GAME.ANALYZED_FROM_BATCH.fixed(), value)
                    .where(REFERENCE_GAME.ID.eq(gameId.id))
                    .awaitExecute()

                PVP -> DSL
                    .using(cfg)
                    .update(GAME.fixed())
                    .set(GAME.ANALYZED_FROM_BATCH.fixed(), value)
                    .where(GAME.ID.eq(gameId.id))
                    .awaitExecute()

                PVB -> DSL
                    .using(cfg)
                    .update(BOT_GAME.fixed())
                    .set(BOT_GAME.ANALYZED_FROM_BATCH.fixed(), value)
                    .where(BOT_GAME.ID.eq(gameId.id))
                    .awaitExecute()
            }
        }
    }

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
        val gameTypeExpr = DSL
            .`when`(MOVE_ANALYSIS.GAME_ID.isNotNull, DSL.inline(PVP.name))
            .`when`(MOVE_ANALYSIS.BOT_GAME_ID.isNotNull, DSL.inline(PVB.name))
            .otherwise(DSL.inline(DB.name))

        val gameIdExpr = DSL.coalesce(
            MOVE_ANALYSIS.GAME_ID,
            MOVE_ANALYSIS.BOT_GAME_ID,
            MOVE_ANALYSIS.REF_GAME_ID
        )

        val aggregatedMoveAnalysis = dslContext
            .select(
                gameTypeExpr.`as`(GAME_TYPE),
                gameIdExpr.`as`(GAME_ID),
                DSL.min(MOVE_ANALYSIS.ENTRY_CREATION).`as`(MIN_DATE),
                DSL.max(MOVE_ANALYSIS.ENTRY_CREATION).`as`(MAX_DATE),
                DSL.count().cast(Int::class.java).`as`(TOTAL_MOVES)
            )
            .from(MOVE_ANALYSIS)
            .groupBy(gameTypeExpr, gameIdExpr)
            .asTable("aggregated_move_analysis")

        val analysisFlags = DSL
            .select(
                DSL.inline(PVP.name).`as`(GAME_TYPE),
                GAME.ID.`as`(GAME_ID),
                GAME.ANALYZED_FROM_BATCH.`as`(ANALYZED_FROM_BATCH)
            )
            .from(GAME)
            .unionAll(
                DSL.select(
                    DSL.inline(PVB.name).`as`(GAME_TYPE),
                    BOT_GAME.ID.`as`(GAME_ID),
                    BOT_GAME.ANALYZED_FROM_BATCH.`as`(ANALYZED_FROM_BATCH)
                ).from(BOT_GAME)
            )
            .unionAll(
                DSL.select(
                    DSL.inline(DB.name).`as`(GAME_TYPE),
                    REFERENCE_GAME.ID.`as`(GAME_ID),
                    REFERENCE_GAME.ANALYZED_FROM_BATCH.`as`(ANALYZED_FROM_BATCH)
                ).from(REFERENCE_GAME)
            )
            .asTable("analysis_flags")

        val aggGameType = aggregatedMoveAnalysis.field(GAME_TYPE, String::class.java)!!
        val aggGameId = aggregatedMoveAnalysis.field(GAME_ID, String::class.java)!!
        val aggMinDate = aggregatedMoveAnalysis.field(MIN_DATE, Instant::class.java)!!
        val aggMaxDate = aggregatedMoveAnalysis.field(MAX_DATE, Instant::class.java)!!
        val aggTotalMoves = aggregatedMoveAnalysis.field(TOTAL_MOVES, Int::class.java)!!

        val flagGameType = analysisFlags.field(GAME_TYPE, String::class.java)!!
        val flagGameId = analysisFlags.field(GAME_ID, String::class.java)!!
        val flagAnalyzedFromBatch = analysisFlags.field(ANALYZED_FROM_BATCH, Boolean::class.java)!!

        return dslContext
            .select(
                aggGameType,
                aggGameId,
                aggMinDate,
                aggMaxDate,
                aggTotalMoves,
                flagAnalyzedFromBatch
            )
            .from(aggregatedMoveAnalysis)
            .leftJoin(analysisFlags)
            .on(aggGameType.eq(flagGameType).and(aggGameId.eq(flagGameId)))
            .orderBy(aggMaxDate.desc())
            .limit(limit)
            .awaitRecords()
            .map { record ->
                MoveAnalysisDataGameEntryRecord(
                    GameId(GameType.valueOf(record[aggGameType]), record[aggGameId]),
                    record[aggMinDate],
                    record[aggMaxDate],
                    record[aggTotalMoves],
                    record[flagAnalyzedFromBatch] ?: false
                )
            }
    }

    suspend fun fetchLatestMoveAnalysis(): Instant? {
        return dslContext
            .select(DSL.max(MOVE_ANALYSIS.ENTRY_CREATION))
            .from(MOVE_ANALYSIS)
            .awaitSingleValue()
    }

    suspend fun isAnyGameCurrentlyBeingAnalyzed(): Boolean {
        val refGameCount = dslContext
            .selectCount()
            .from(REFERENCE_GAME)
            .where(REFERENCE_GAME.ANALYSIS_STATUS.eq(STARTED))
            .awaitSingleValue() ?: 0

        if (refGameCount > 0) return true

        val pvpGameCount = dslContext
            .selectCount()
            .from(GAME)
            .where(GAME.ANALYSIS_STATUS.eq(STARTED))
            .awaitSingleValue() ?: 0

        if (pvpGameCount > 0) return true

        val pvbGameCount = dslContext
            .selectCount()
            .from(BOT_GAME)
            .where(BOT_GAME.ANALYSIS_STATUS.eq(STARTED))
            .awaitSingleValue() ?: 0

        return pvbGameCount > 0
    }

    private companion object {

        const val GAME_TYPE = "game_type"
        const val GAME_ID = "game_data_id"
        const val ANALYZED_FROM_BATCH = "analyzed_from_batch"
        const val MIN_DATE = "min_date"
        const val MAX_DATE = "max_date"
        const val TOTAL_MOVES = "total_moves"

        // jOOQ's DefaultDataType registry does not know about kotlin.time.Instant,
        // so we attach the same converter the codegen uses for timestamptz columns.
        val INSTANT_TYPE: DataType<Instant> =
            SQLDataType.TIMESTAMPWITHTIMEZONE(6).asConvertedDataType(OffsetDateTimeInstantConverter())

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
