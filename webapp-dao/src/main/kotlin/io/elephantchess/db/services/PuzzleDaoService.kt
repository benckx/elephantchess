package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.PuzzleCategoryTagDao
import io.elephantchess.db.dao.codegen.tables.daos.PuzzleDao
import io.elephantchess.db.dao.codegen.tables.daos.PuzzleHalfMoveDao
import io.elephantchess.db.dao.codegen.tables.pojos.Puzzle
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleCategoryTag
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleHalfMove
import io.elephantchess.db.model.PuzzleDailyStatsEntry
import io.elephantchess.db.model.PuzzleRecord
import io.elephantchess.db.model.PuzzleResultCountsRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.PuzzleCategory
import io.elephantchess.model.PuzzleOutcome
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.LocalDate

class PuzzleDaoService(private val dslContext: DSLContext) {

    suspend fun save(puzzle: Puzzle, moves: List<PuzzleHalfMove>, categories: List<PuzzleCategoryTag>) {
        dslContext.transactionCoroutine { cfg ->
            PuzzleDao(cfg).insertReactive(puzzle)
            PuzzleHalfMoveDao(cfg).insertMultipleReactive(moves)
            PuzzleCategoryTagDao(cfg).insertMultipleReactive(categories)
        }
    }

    suspend fun fetchById(id: String): PuzzleRecord {
        return PuzzleRecord(
            puzzle = dslContext
                .selectFrom(PUZZLE)
                .where(PUZZLE.ID.eq(id))
                .awaitSingleMappedRecord()!!,
            moves = dslContext
                .selectFrom(PUZZLE_HALF_MOVE)
                .where(PUZZLE_HALF_MOVE.PUZZLE_ID.eq(id))
                .orderBy(PUZZLE_HALF_MOVE.POSITION)
                .awaitMappedRecords(),
            categories = dslContext
                .selectFrom(PUZZLE_CATEGORY_TAG)
                .where(PUZZLE_CATEGORY_TAG.PUZZLE_ID.eq(id))
                .awaitMappedRecords()
        )
    }

    suspend fun fetchAll(): List<Puzzle> {
        return dslContext
            .selectFrom(PUZZLE)
            .awaitMappedRecords()
    }

    suspend fun fetchAllCategories(): Map<String, List<PuzzleCategory>> {
        return dslContext
            .selectFrom(PUZZLE_CATEGORY_TAG)
            .awaitMappedRecords<PuzzleCategoryTag>()
            .groupBy { record -> record.puzzleId }
            .map { (puzzleId, categories) ->
                puzzleId to categories.map { it.category }
            }
            .toMap()
    }

    suspend fun findPuzzleRatingOfUser(userId: String): Int {
        return dslContext
            .select(USER.PUZZLE_RATING)
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleValue()!!
    }

    suspend fun findCurrentlyAssignedTo(userId: String): String? {
        return dslContext
            .select(USER.LAST_PUZZLE_ASSIGNED)
            .from(USER)
            .where(USER.ID.eq((userId)))
            .awaitSingleValue()
    }

    // TODO: make async?
    suspend fun assign(userId: String, puzzleId: String) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER.fixed())
                .set(USER.LAST_PUZZLE_ASSIGNED.fixed(), puzzleId)
                .where(USER.ID.eq(userId))
                .awaitExecute()
        }
    }

    suspend fun unAssign(userId: String) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER.fixed())
                .setNull(USER.LAST_PUZZLE_ASSIGNED.fixed())
                .where(USER.ID.eq(userId))
                .awaitExecute()
        }
    }

    suspend fun fetchRatingForUser(userId: String): Int? {
        return dslContext
            .select(USER.PUZZLE_RATING)
            .from(USER)
            .where(USER.ID.eq((userId)))
            .awaitSingleValue()
    }

    suspend fun fetchMaxRatingForUser(userId: String): Int? {
        return dslContext
            .select(DSL.max(PUZZLE_RESULT.PLAYER_RATING_TO))
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.USER_ID.eq((userId)))
            .awaitSingleValue()
    }

    suspend fun countPlayedForUser(userId: String): Int? {
        return dslContext
            .select(DSL.countDistinct(PUZZLE_RESULT.PUZZLE_ID))
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.USER_ID.eq((userId)))
            .awaitSingleValue()
    }

    /**
     * To build the bar chart on user profile
     */
    suspend fun fetchPuzzleStatsLastRating(userId: String): List<PuzzleDailyStatsEntry> {
        // last id per each day
        val pr2 =
            dslContext
                .select(
                    PUZZLE_RESULT.ENTRY_CREATION.date().`as`("day"),
                    DSL.max(PUZZLE_RESULT.ID).`as`("max_id")
                )
                .from(PUZZLE_RESULT)
                .where(PUZZLE_RESULT.USER_ID.eq(userId))
                .groupBy(PUZZLE_RESULT.USER_ID, DSL.field("day"))
                .orderBy(PUZZLE_RESULT.USER_ID, DSL.field("day"))
                .asTable("pr2")

        // most recent rating per day
        return dslContext
            .select(
                PUZZLE_RESULT.ENTRY_CREATION.dateQualified("pr1").`as`("day"),
                PUZZLE_RESULT.PLAYER_RATING_TO.qualified("pr1").`as`("last_rating")
            )
            .from(PUZZLE_RESULT.`as`("pr1"), pr2)
            .where(PUZZLE_RESULT.USER_ID.qualified("pr1").eq(userId))
            .and(PUZZLE_RESULT.ID.qualified("pr1").eq(DSL.field("pr2.max_id")))
            .awaitRecords()
            .map { record2 ->
                PuzzleDailyStatsEntry(
                    date = LocalDate.parse(record2.value1()!!.toString()),
                    value = record2.value2()!!.toString().toInt()
                )
            }
            .toList()
    }

    /**
     * To build the bar chart on user profile
     */
    suspend fun fetchPuzzleStatsMaxRating(userId: String): List<PuzzleDailyStatsEntry> {
        return dslContext
            .select(
                PUZZLE_RESULT.ENTRY_CREATION.date().`as`("day"),
                DSL.max(PUZZLE_RESULT.PLAYER_RATING_TO)
            )
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.USER_ID.eq(userId))
            .groupBy(DSL.field("day"))
            .orderBy(DSL.field("day"))
            .awaitRecords()
            .map { record2 ->
                PuzzleDailyStatsEntry(
                    date = LocalDate.parse(record2.value1()!!.toString()),
                    value = record2.value2()!!.toString().toInt()
                )
            }
            .toList()
    }

    suspend fun fetchPuzzleStatsNumberPerOutcome(userId: String, outcome: PuzzleOutcome): List<PuzzleDailyStatsEntry> {
        return dslContext
            .select(
                PUZZLE_RESULT.ENTRY_CREATION.date().`as`("day"),
                DSL.count()
            )
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.USER_ID.eq(userId))
            .and(PUZZLE_RESULT.OUTCOME.eq(outcome))
            .groupBy(DSL.field("day"))
            .orderBy(DSL.field("day"))
            .awaitRecords()
            .map { record2 ->
                PuzzleDailyStatsEntry(
                    date = LocalDate.parse(record2.value1()!!.toString()),
                    value = record2.value2()!!.toString().toInt()
                )
            }
            .toList()
    }

    suspend fun fetchPuzzleResultCounts(userIds: List<String>): List<PuzzleResultCountsRecord> {
        val solvedField = DSL.count().filterWhere(PUZZLE_RESULT.OUTCOME.eq(PuzzleOutcome.SOLVED)).`as`("nbr_solved")
        val failedField = DSL.count().filterWhere(PUZZLE_RESULT.OUTCOME.eq(PuzzleOutcome.FAILED)).`as`("nbr_failed")
        val totalField = DSL.count().`as`("nbr_total")

        return dslContext
            .select(PUZZLE_RESULT.USER_ID, solvedField, failedField, totalField)
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.USER_ID.`in`(userIds))
            .groupBy(PUZZLE_RESULT.USER_ID)
            .awaitRecords()
            .map { record ->
                PuzzleResultCountsRecord(
                    userId = record.value1()!!,
                    solved = record.value2(),
                    failed = record.value3(),
                    total = record.value4()
                )
            }
    }

}
