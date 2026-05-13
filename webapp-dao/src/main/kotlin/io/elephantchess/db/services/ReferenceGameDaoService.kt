package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.ReferenceGameHalfMove.REFERENCE_GAME_HALF_MOVE
import io.elephantchess.db.dao.codegen.tables.daos.*
import io.elephantchess.db.dao.codegen.tables.pojos.*
import io.elephantchess.db.dao.codegen.tables.records.ReferenceGameEventRecord
import io.elephantchess.db.dao.codegen.tables.records.ReferenceGameOpeningRecord
import io.elephantchess.db.dao.codegen.tables.records.ReferenceGameOpeningVariationRecord
import io.elephantchess.db.dao.codegen.tables.records.ReferenceGameRecord
import io.elephantchess.db.model.EntityIdAndNameRecord
import io.elephantchess.db.model.ReferenceGamePojo
import io.elephantchess.db.model.UserDbSearchEntry
import io.elephantchess.db.utils.*
import io.elephantchess.model.AnalysisStatus
import io.elephantchess.model.AnalysisStatus.CANCELLED
import io.elephantchess.model.AnalysisStatus.STARTED
import io.elephantchess.xiangqi.Color
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.DSL.lower
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class ReferenceGameDaoService(private val dslContext: DSLContext) {

    suspend fun saveEvents(events: List<ReferenceGameEvent>) {
        dslContext.transactionCoroutine { cfg ->
            ReferenceGameEventDao(cfg).insertMultipleReactive(events)
        }
    }

    suspend fun saveOpenings(openings: List<ReferenceGameOpening>) {
        dslContext.transactionCoroutine { cfg ->
            ReferenceGameOpeningDao(cfg).insertMultipleReactive(openings)
        }
    }

    suspend fun saveVariations(variations: List<ReferenceGameOpeningVariation>) {
        dslContext.transactionCoroutine { cfg ->
            ReferenceGameOpeningVariationDao(cfg).insertMultipleReactive(variations)
        }
    }

    suspend fun saveGame(pojo: ReferenceGamePojo) {
        dslContext.transactionCoroutine { cfg ->
            ReferenceGameDao(cfg).insertReactive(pojo.game)
            ReferenceGameHalfMoveDao(cfg).insertMultipleReactive(pojo.moves)
        }
    }

    suspend fun listPreAnalyzedGamesByYear(): List<Record3<Int, AnalysisStatus, Int>> {
        return dslContext
            .select(REFERENCE_GAME.YEAR, REFERENCE_GAME.ANALYSIS_STATUS, DSL.count().`as`("count"))
            .from(REFERENCE_GAME)
            .where(REFERENCE_GAME.YEAR.isNotNull)
            .groupBy(REFERENCE_GAME.YEAR, REFERENCE_GAME.ANALYSIS_STATUS)
            .orderBy(REFERENCE_GAME.YEAR.desc(), REFERENCE_GAME.ANALYSIS_STATUS.asc())
            .awaitRecords()
    }

    suspend fun findByEventId(eventId: String) : List<ReferenceGameRecord> {
        return dslContext
            .select()
            .from(REFERENCE_GAME)
            .where(REFERENCE_GAME.EVENT.eq(eventId))
            .awaitMappedRecords()
    }

    suspend fun findById(gameId: String): ReferenceGameRecord? {
        return dslContext
            .select()
            .from(REFERENCE_GAME)
            .where(REFERENCE_GAME.ID.eq(gameId))
            .awaitSingleMappedRecord()
    }

    suspend fun countAllGames(): Int {
        return dslContext
            .select(DSL.count())
            .from(REFERENCE_GAME)
            .awaitSingleValue()!!
    }

    suspend fun listMoves(gameId: String): List<String> {
        return dslContext
            .select(REFERENCE_GAME_HALF_MOVE.UCI)
            .from(REFERENCE_GAME_HALF_MOVE)
            .where(REFERENCE_GAME_HALF_MOVE.REFERENCE_GAME_ID.eq(gameId))
            .orderBy(REFERENCE_GAME_HALF_MOVE.POSITION)
            .awaitMappedRecords()
    }

    suspend fun countTotalMoves(): Int {
        return dslContext
            .selectCount()
            .from(REFERENCE_GAME_HALF_MOVE)
            .awaitSingleValue()!!
    }

    suspend fun countTotalMoves(gameId: String): Int? {
        return dslContext
            .select(REFERENCE_GAME.NUMBER_OF_HALF_MOVES)
            .from(REFERENCE_GAME)
            .where(REFERENCE_GAME.ID.eq(gameId))
            .awaitSingleValue()
    }


    suspend fun listAllEvents(): List<ReferenceGameEventRecord> {
        return dslContext
            .select()
            .from(REFERENCE_GAME_EVENT)
            .orderBy(REFERENCE_GAME_EVENT.NAME)
            .awaitMappedRecords()
    }

    suspend fun listEvents(contains: String, limit: Int): List<EntityIdAndNameRecord> {
        return dslContext
            .selectDistinct(REFERENCE_GAME_EVENT.ID, REFERENCE_GAME_EVENT.NAME)
            .from(REFERENCE_GAME_EVENT)
            .where(lower(REFERENCE_GAME_EVENT.NAME).contains(contains.lowercase()))
            .and(REFERENCE_GAME_EVENT.IS_VISIBLE.eq(true))
            .orderBy(REFERENCE_GAME_EVENT.NAME)
            .limit(limit)
            .awaitRecords()
            .map { record2 -> EntityIdAndNameRecord(record2.value1(), record2.value2()) }
            .toList()
    }

    suspend fun listAllOpenings(): List<ReferenceGameOpeningRecord> {
        return dslContext
            .select()
            .from(REFERENCE_GAME_OPENING)
            .orderBy(REFERENCE_GAME_OPENING.NAME)
            .awaitMappedRecords()
    }

    suspend fun listAllOpeningVariations(): List<ReferenceGameOpeningVariationRecord> {
        return dslContext
            .select()
            .from(REFERENCE_GAME_OPENING_VARIATION)
            .orderBy(REFERENCE_GAME_OPENING_VARIATION.NAME)
            .awaitMappedRecords()
    }

    suspend fun listAllSourceReferenceIds(): List<String> {
        return dslContext
            .selectDistinct(REFERENCE_GAME.SOURCE_ID)
            .from(REFERENCE_GAME)
            .awaitMappedRecords()
    }

    suspend fun findByPuzzleIds(puzzleIds: List<String>): List<Record> {
        return if (puzzleIds.isEmpty()) {
            emptyList()
        } else {
            dslContext
                .select(
                    PUZZLE.ID,
                    REFERENCE_GAME.ID,
                    REFERENCE_GAME.FINAL_FEN,
                    REFERENCE_GAME.ANALYSIS_STATUS,
                    REFERENCE_GAME.RED_PLAYER,
                    REFERENCE_GAME.BLACK_PLAYER,
                    REFERENCE_PLAYER.ID,
                    REFERENCE_PLAYER.CANONICAL_NAME,
                    REFERENCE_GAME.OUTCOME,
                    REFERENCE_GAME.DATE,
                )
                .from(
                    PUZZLE,
                    REFERENCE_GAME,
                    REFERENCE_PLAYER
                )
                .where(PUZZLE.ID.`in`(puzzleIds))
                .and(PUZZLE.REF_GAME_SOURCE_ID.eq(REFERENCE_GAME.SOURCE_ID))
                .and(
                    REFERENCE_PLAYER.ID.eq(REFERENCE_GAME.RED_PLAYER)
                        .or(REFERENCE_PLAYER.ID.eq(REFERENCE_GAME.BLACK_PLAYER))
                )
                .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
                .awaitRecords()
        }
    }

    // might be useful later
    @Suppress("unused")
    private suspend fun listAllGamesBeginningWithMoves(moves: List<String>): List<String> {
        if (moves.isEmpty()) {
            return emptyList()
        } else {
            var condition =
                REFERENCE_GAME_HALF_MOVE.POSITION.eq(0)
                    .and(REFERENCE_GAME_HALF_MOVE.UCI.eq(moves.first()))

            moves.drop(1).forEachIndexed { i, uci ->
                condition = condition
                    .or(REFERENCE_GAME_HALF_MOVE.POSITION.eq(i + 1).and(REFERENCE_GAME_HALF_MOVE.UCI.eq(uci)))
            }

            // games where at least 1 <move, position> tuple is matching
            val intermediaryTable =
                dslContext
                    .select(
                        REFERENCE_GAME_HALF_MOVE.REFERENCE_GAME_ID.`as`("game_id"),
                        REFERENCE_GAME_HALF_MOVE.POSITION,
                        REFERENCE_GAME_HALF_MOVE.UCI
                    )
                    .from(REFERENCE_GAME_HALF_MOVE)
                    .where(condition)
                    .orderBy(REFERENCE_GAME_HALF_MOVE.REFERENCE_GAME_ID, REFERENCE_GAME_HALF_MOVE.POSITION)

            // we filter games where all moves are matching with COUNT (nbr of matching moves == nbr of moves in the query)
            val matchingGamesTable =
                dslContext
                    .select(DSL.field("game_id"), DSL.count().`as`("count"))
                    .from(intermediaryTable)
                    .groupBy(DSL.field("game_id"))
                    .having(DSL.count().eq(moves.size))
                    .asTable("matching_games")

            return dslContext
                .select(DSL.field("game_id"))
                .from(matchingGamesTable)
                .awaitMappedRecords()
        }
    }

    suspend fun findLatestGameDates(playerIds: List<String>): Map<String, LocalDate> {
        if (playerIds.isEmpty()) return emptyMap()

        // create a union of all games where the player participated
        val allGamesUnion = dslContext
            .select(
                REFERENCE_GAME.RED_PLAYER.`as`("player_id"),
                REFERENCE_GAME.DATE
            )
            .from(REFERENCE_GAME)
            .where(REFERENCE_GAME.RED_PLAYER.`in`(playerIds))
            .and(REFERENCE_GAME.DATE.isNotNull)
            .unionAll(
                dslContext
                    .select(
                        REFERENCE_GAME.BLACK_PLAYER.`as`("player_id"),
                        REFERENCE_GAME.DATE
                    )
                    .from(REFERENCE_GAME)
                    .where(REFERENCE_GAME.BLACK_PLAYER.`in`(playerIds))
                    .and(REFERENCE_GAME.DATE.isNotNull)
            )
            .asTable("all_games")

        // group by player and get max date
        return dslContext
            .select(
                allGamesUnion.field("player_id", String::class.java),
                DSL.max(allGamesUnion.field("date", LocalDate::class.java))
            )
            .from(allGamesUnion)
            .groupBy(allGamesUnion.field("player_id", String::class.java))
            .awaitRecords()
            .mapNotNull { record ->
                val playerId = record.get(0, String::class.java)
                val maxDate = record.get(1, LocalDate::class.java)
                if (playerId != null && maxDate != null) {
                    playerId to maxDate
                } else {
                    null
                }
            }
            .toMap()
    }

    suspend fun search(
        year: Int? = null,
        dateStart: LocalDate? = null,
        dateEnd: LocalDate? = null,
        playerIds: List<String> = listOf(),
        playerColor: Color? = null,
        eventIds: List<String> = listOf(),
        round: Int? = null,
        fen: String? = null,
        limit: Int,
        offset: Int? = null
    ): List<ReferenceGame> {
        val conditions = mutableListOf<Condition>()

        if (year != null) {
            conditions += REFERENCE_GAME.YEAR.eq(year)
        }
        if (dateStart != null) {
            conditions += REFERENCE_GAME.DATE.greaterOrEqual(dateStart)
        }
        if (dateEnd != null) {
            conditions += REFERENCE_GAME.DATE.lessOrEqual(dateEnd)
        }

        if (playerIds.isNotEmpty()) {
            conditions += if (playerColor != null) {
                // only one color
                when (playerColor) {
                    Color.RED -> REFERENCE_GAME.RED_PLAYER.`in`(playerIds)
                    Color.BLACK -> REFERENCE_GAME.BLACK_PLAYER.`in`(playerIds)
                }
            } else {
                // both colors
                REFERENCE_GAME.RED_PLAYER.`in`(playerIds)
                    .or(REFERENCE_GAME.BLACK_PLAYER.`in`(playerIds))
            }
        }

        if (eventIds.isNotEmpty()) {
            conditions += REFERENCE_GAME.EVENT.`in`(eventIds)
        }

        if (round != null) {
            conditions += REFERENCE_GAME.ROUND.eq(round)
        }

        if (conditions.isEmpty()) {
            conditions += DSL.trueCondition()
        }

        val tables = mutableListOf<Table<*>>()
        tables += REFERENCE_GAME

        if (fen != null) {
            tables += REFERENCE_GAME_HALF_MOVE
            conditions += REFERENCE_GAME.ID.eq(REFERENCE_GAME_HALF_MOVE.REFERENCE_GAME_ID)
            conditions += REFERENCE_GAME_HALF_MOVE.FEN.eq(fen)
        }

        val query =
            dslContext
                .select()
                .from(tables)
                .where(conditions)
                .orderBy(
                    REFERENCE_GAME.DATE.desc().nullsLast(),
                    REFERENCE_GAME.SOURCE_ID // make order deterministic
                )
                .limit(limit)

        return if (offset != null) {
            query.offset(offset).awaitMappedRecords()
        } else {
            query.awaitMappedRecords()
        }
    }

    suspend fun persistSearch(
        userId: String,
        searchStart: LocalDate?,
        searchEnd: LocalDate?,
        playerName: String?,
        playerId: String?,
        playerColor: Color?,
        eventName: String?,
        eventId: String?,
        fen: String?,
        offset: Int?,
        limit: Int,
        numberOfResults: Int
    ) {
        // Only track the first page (offset null or 0) to avoid duplicate entries for pagination
        if (offset != null && offset > 0) {
            return
        }

        val now = Clock.System.now()
        val updateField = DSL.field("update_time", Instant::class.java)

        val existingId = dslContext
            .select(REFERENCE_GAME_SEARCH_QUERY.QUERY_ID)
            .from(REFERENCE_GAME_SEARCH_QUERY)
            .where(REFERENCE_GAME_SEARCH_QUERY.USER_ID.eq(userId))
            .and(
                if (playerName != null) REFERENCE_GAME_SEARCH_QUERY.PLAYER_NAME.eq(playerName)
                else REFERENCE_GAME_SEARCH_QUERY.PLAYER_NAME.isNull
            )
            .and(
                if (playerId != null) REFERENCE_GAME_SEARCH_QUERY.PLAYER_ID.eq(playerId)
                else REFERENCE_GAME_SEARCH_QUERY.PLAYER_ID.isNull
            )
            .and(
                if (playerColor != null) REFERENCE_GAME_SEARCH_QUERY.PLAYER_COLOR.eq(playerColor)
                else REFERENCE_GAME_SEARCH_QUERY.PLAYER_COLOR.isNull
            )
            .and(
                if (eventName != null) REFERENCE_GAME_SEARCH_QUERY.EVENT_NAME.eq(eventName)
                else REFERENCE_GAME_SEARCH_QUERY.EVENT_NAME.isNull
            )
            .and(
                if (eventId != null) REFERENCE_GAME_SEARCH_QUERY.EVENT_ID.eq(eventId)
                else REFERENCE_GAME_SEARCH_QUERY.EVENT_ID.isNull
            )
            .and(
                if (searchStart != null) REFERENCE_GAME_SEARCH_QUERY.SEARCH_START.eq(searchStart)
                else REFERENCE_GAME_SEARCH_QUERY.SEARCH_START.isNull
            )
            .and(
                if (searchEnd != null) REFERENCE_GAME_SEARCH_QUERY.SEARCH_END.eq(searchEnd)
                else REFERENCE_GAME_SEARCH_QUERY.SEARCH_END.isNull
            )
            .and(
                if (fen != null) REFERENCE_GAME_SEARCH_QUERY.FEN.eq(fen)
                else REFERENCE_GAME_SEARCH_QUERY.FEN.isNull
            )
            .awaitSingleValue<String>()

        if (existingId != null) {
            dslContext
                .update(REFERENCE_GAME_SEARCH_QUERY)
                .set(updateField.fixed(), now)
                .set(REFERENCE_GAME_SEARCH_QUERY.NUMBER_OF_RESULTS.fixed(), numberOfResults)
                .where(REFERENCE_GAME_SEARCH_QUERY.QUERY_ID.eq(existingId))
                .awaitExecute()
            return
        }

        val queryId = generateId()
        dslContext
            .insertInto(REFERENCE_GAME_SEARCH_QUERY.fixed())
            .set(REFERENCE_GAME_SEARCH_QUERY.QUERY_ID.fixed(), queryId)
            .set(REFERENCE_GAME_SEARCH_QUERY.USER_ID.fixed(), userId)
            .set(REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME.fixed(), now)
            .set(updateField.fixed(), now)
            .set(REFERENCE_GAME_SEARCH_QUERY.SEARCH_START.fixed(), searchStart)
            .set(REFERENCE_GAME_SEARCH_QUERY.SEARCH_END.fixed(), searchEnd)
            .set(REFERENCE_GAME_SEARCH_QUERY.PLAYER_NAME.fixed(), playerName)
            .set(REFERENCE_GAME_SEARCH_QUERY.PLAYER_ID.fixed(), playerId)
            .set(REFERENCE_GAME_SEARCH_QUERY.PLAYER_COLOR.fixed(), playerColor)
            .set(REFERENCE_GAME_SEARCH_QUERY.EVENT_NAME.fixed(), eventName)
            .set(REFERENCE_GAME_SEARCH_QUERY.EVENT_ID.fixed(), eventId)
            .set(REFERENCE_GAME_SEARCH_QUERY.FEN.fixed(), fen)
            .set(REFERENCE_GAME_SEARCH_QUERY.OFFSET.fixed(), offset)
            .set(REFERENCE_GAME_SEARCH_QUERY.LIMIT.fixed(), limit)
            .set(REFERENCE_GAME_SEARCH_QUERY.NUMBER_OF_RESULTS.fixed(), numberOfResults)
            .awaitExecute()
    }

    suspend fun listUserSearches(userId: String, beforeTime: Instant?, limit: Int): List<UserDbSearchEntry> {
        val updateField = DSL.field("update_time", Instant::class.java)

        val condition = REFERENCE_GAME_SEARCH_QUERY.USER_ID.eq(userId)
            .and(REFERENCE_GAME_SEARCH_QUERY.OFFSET.isNull.or(REFERENCE_GAME_SEARCH_QUERY.OFFSET.eq(0)))
            .let { cond ->
                if (beforeTime != null) cond.and(updateField.lessThan(beforeTime))
                else cond
            }

        return dslContext
            .select(
                REFERENCE_GAME_SEARCH_QUERY.QUERY_ID,
                REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME,
                updateField,
                REFERENCE_GAME_SEARCH_QUERY.PLAYER_NAME,
                REFERENCE_GAME_SEARCH_QUERY.PLAYER_ID,
                REFERENCE_GAME_SEARCH_QUERY.PLAYER_COLOR,
                REFERENCE_GAME_SEARCH_QUERY.EVENT_NAME,
                REFERENCE_GAME_SEARCH_QUERY.EVENT_ID,
                REFERENCE_GAME_SEARCH_QUERY.SEARCH_START,
                REFERENCE_GAME_SEARCH_QUERY.SEARCH_END,
                REFERENCE_GAME_SEARCH_QUERY.FEN,
                REFERENCE_GAME_SEARCH_QUERY.NUMBER_OF_RESULTS,
            )
            .from(REFERENCE_GAME_SEARCH_QUERY)
            .where(condition)
            .orderBy(DSL.coalesce(updateField, REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME).desc())
            .limit(limit)
            .awaitRecords()
            .map { record ->
                UserDbSearchEntry(
                    queryId = record.get(REFERENCE_GAME_SEARCH_QUERY.QUERY_ID),
                    queryTime = record.get(REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME),
                    updateTime = record.get(updateField) ?: record.get(REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME),
                    playerName = record.get(REFERENCE_GAME_SEARCH_QUERY.PLAYER_NAME),
                    playerId = record.get(REFERENCE_GAME_SEARCH_QUERY.PLAYER_ID),
                    playerColor = record.get(REFERENCE_GAME_SEARCH_QUERY.PLAYER_COLOR),
                    eventName = record.get(REFERENCE_GAME_SEARCH_QUERY.EVENT_NAME),
                    eventId = record.get(REFERENCE_GAME_SEARCH_QUERY.EVENT_ID),
                    searchStart = record.get(REFERENCE_GAME_SEARCH_QUERY.SEARCH_START),
                    searchEnd = record.get(REFERENCE_GAME_SEARCH_QUERY.SEARCH_END),
                    fen = record.get(REFERENCE_GAME_SEARCH_QUERY.FEN),
                    numberOfResults = record.get(REFERENCE_GAME_SEARCH_QUERY.NUMBER_OF_RESULTS),
                )
            }
    }

    suspend fun listLatestDatabaseSearches(limit: Int): List<ReferenceGameSearchQuery> {
        return dslContext
            .selectFrom(REFERENCE_GAME_SEARCH_QUERY)
            .orderBy(REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    suspend fun listPreAnalysisToDelete(limit: Duration): List<Pair<String, Instant>> {
        return dslContext
            .select(
                REFERENCE_GAME.ID,
                REFERENCE_GAME.ANALYSIS_START_TIME
            )
            .from(REFERENCE_GAME)
            .where(REFERENCE_GAME.ANALYSIS_STATUS.`in`(STARTED, CANCELLED))
            .and(REFERENCE_GAME.ANALYSIS_START_TIME.isOlderThan(limit))
            .awaitRecords()
            .map { record2 ->
                Pair(
                    record2.get(REFERENCE_GAME.ID),
                    record2.get(REFERENCE_GAME.ANALYSIS_START_TIME)
                )
            }
    }

}
