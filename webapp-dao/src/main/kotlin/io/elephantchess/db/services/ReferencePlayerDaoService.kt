package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.ReferencePlayerDao
import io.elephantchess.db.dao.codegen.tables.pojos.ReferencePlayer
import io.elephantchess.db.dao.codegen.tables.pojos.ReferencePlayerDuplicate
import io.elephantchess.db.dao.codegen.tables.pojos.ReferencePlayerProfileEdit
import io.elephantchess.db.dao.codegen.tables.pojos.ReferencePlayerProfileEditSource
import io.elephantchess.db.dao.codegen.tables.records.ReferenceGameRecord
import io.elephantchess.db.dao.codegen.tables.records.ReferencePlayerRecord
import io.elephantchess.db.model.EntityIdAndNameRecord
import io.elephantchess.db.model.NumberOfGamesRecord
import io.elephantchess.db.model.PlayerGameStatsRecord
import io.elephantchess.db.model.PlayerStandingsRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.Outcome
import io.elephantchess.model.Outcome.*
import io.elephantchess.utils.formatWithChineseName
import org.jooq.AggregateFunction
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.DSL.lower
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Instant

class ReferencePlayerDaoService(private val dslContext: DSLContext) {

    suspend fun savePlayers(players: List<ReferencePlayer>) {
        dslContext.transactionCoroutine { cfg ->
            ReferencePlayerDao(cfg).insertMultipleReactive(players)
        }
    }

    suspend fun findCanonicalPlayerName(id: String): String? {
        return dslContext
            .select(REFERENCE_PLAYER.CANONICAL_NAME)
            .from(REFERENCE_PLAYER)
            .where(REFERENCE_PLAYER.ID.eq(id))
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .awaitSingleValue()
    }

    suspend fun findPlayer(id: String): ReferencePlayer? {
        return dslContext
            .select(REFERENCE_PLAYER)
            .from(REFERENCE_PLAYER)
            .where(REFERENCE_PLAYER.ID.eq(id))
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .awaitSingleMappedRecord()
    }

    suspend fun findPlayerByCanonicalName(canonicalPlayerName: String): ReferencePlayer? {
        return dslContext
            .select(REFERENCE_PLAYER)
            .from(REFERENCE_PLAYER)
            .where(REFERENCE_PLAYER.CANONICAL_NAME.eq(canonicalPlayerName))
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .awaitSingleMappedRecord()
    }

    suspend fun existsByCanonicalName(canonicalName: String, excludedPlayerId: String): Boolean {
        val count: Int = dslContext
            .select(DSL.count())
            .from(REFERENCE_PLAYER)
            .where(REFERENCE_PLAYER.CANONICAL_NAME.eq(canonicalName))
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .and(REFERENCE_PLAYER.ID.ne(excludedPlayerId))
            .awaitSingleValue() ?: 0

        return count > 0
    }

    suspend fun findEditedPlayersByEditorId(editorId: String): List<ReferencePlayerProfileEdit> {
        val v2 = REFERENCE_PLAYER_PROFILE_EDIT.`as`("v2")

        return dslContext
            .select(REFERENCE_PLAYER_PROFILE_EDIT)
            .from(REFERENCE_PLAYER_PROFILE_EDIT)
            .where(
                REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID.`in`(
                    dslContext
                        .selectDistinct(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID)
                        .from(REFERENCE_PLAYER_PROFILE_EDIT)
                        .where(REFERENCE_PLAYER_PROFILE_EDIT.EDITOR_ID.eq(editorId))
                )
            )
            .and(
                REFERENCE_PLAYER_PROFILE_EDIT.VERSION.eq(
                    dslContext
                        .select(DSL.max(v2.VERSION))
                        .from(v2)
                        .where(v2.PLAYER_ID.eq(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID))
                )
            )
            .orderBy(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME.desc())
            .awaitMappedRecords()
    }

    suspend fun findPlayerByAnyName(playerName: String): ReferencePlayer? {
        val lowerPlayerName = playerName.lowercase()
        return dslContext
            .select()
            .from(REFERENCE_PLAYER)
            .where(
                lower(REFERENCE_PLAYER.CANONICAL_NAME).eq(lowerPlayerName)
                    .or(lower(REFERENCE_PLAYER.SOURCE_NAME).eq(lowerPlayerName))
                    .or(REFERENCE_PLAYER.CHINESE_NAME.eq(playerName))
            )
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .awaitSingleMappedRecord()
    }

    suspend fun findPlayersByCanonicalNames(canonicalNames: List<String>): List<ReferencePlayer> {
        if (canonicalNames.isEmpty()) return emptyList()

        val lowerNames = canonicalNames.map { it.lowercase() }
        return dslContext
            .select()
            .from(REFERENCE_PLAYER)
            .where(lower(REFERENCE_PLAYER.CANONICAL_NAME).`in`(lowerNames))
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .awaitMappedRecords()
    }

    suspend fun findPlayerByHistoricalName(playerName: String, ignoreCase: Boolean): ReferencePlayer? {
        val whereCondition = if (ignoreCase) {
            val lowerPlayerName = playerName.lowercase()
            lower(REFERENCE_PLAYER_PROFILE_EDIT.CANONICAL_NAME).eq(lowerPlayerName)
                .or(REFERENCE_PLAYER_PROFILE_EDIT.CHINESE_NAME.eq(playerName))
        } else {
            REFERENCE_PLAYER_PROFILE_EDIT.CANONICAL_NAME.eq(playerName)
                .or(REFERENCE_PLAYER_PROFILE_EDIT.CHINESE_NAME.eq(playerName))
        }

        return dslContext
            .select(REFERENCE_PLAYER)
            .from(
                REFERENCE_PLAYER_PROFILE_EDIT,
                REFERENCE_PLAYER
            )
            .where(whereCondition)
            .and(REFERENCE_PLAYER.ID.eq(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID))
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .orderBy(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME.desc())
            .limit(1)
            .awaitSingleMappedRecord()
    }

    suspend fun searchByNames(names: List<String>, excludedPlayerId: String): List<ReferencePlayer> {
        val lowerNames = names.map { it.lowercase() }

        return dslContext
            .select(REFERENCE_PLAYER)
            .from(REFERENCE_PLAYER)
            .where(
                lower(REFERENCE_PLAYER.CANONICAL_NAME).`in`(lowerNames)
                    .or(lower(REFERENCE_PLAYER.SOURCE_NAME).`in`(lowerNames))
            )
            .and(REFERENCE_PLAYER.ID.ne(excludedPlayerId))
            .awaitMappedRecords()
    }

    suspend fun savePlayerDuplicate(playerId: String, isNewDuplicateOf: String) {
        dslContext
            .insertInto(REFERENCE_PLAYER_DUPLICATE)
            .set(REFERENCE_PLAYER_DUPLICATE.PLAYER_ID, playerId)
            .set(REFERENCE_PLAYER_DUPLICATE.IS_DUPLICATE_OF, isNewDuplicateOf)
            .onConflict(REFERENCE_PLAYER_DUPLICATE.PLAYER_ID)
            .doUpdate()
            .set(REFERENCE_PLAYER_DUPLICATE.IS_DUPLICATE_OF, isNewDuplicateOf)
            .awaitExecute()
    }

    suspend fun findConfirmedDuplicatesOf(canonicalPlayerId: String): List<ReferencePlayerDuplicate> {
        return dslContext
            .select(REFERENCE_PLAYER_DUPLICATE)
            .from(REFERENCE_PLAYER_DUPLICATE)
            .where(REFERENCE_PLAYER_DUPLICATE.IS_DUPLICATE_OF.eq(canonicalPlayerId))
            .awaitMappedRecords()
    }

    suspend fun findCanonicalPlayerFor(duplicatePlayerId: String): String? {
        return dslContext
            .select(REFERENCE_PLAYER_DUPLICATE.IS_DUPLICATE_OF)
            .from(REFERENCE_PLAYER_DUPLICATE)
            .where(REFERENCE_PLAYER_DUPLICATE.PLAYER_ID.eq(duplicatePlayerId))
            .awaitSingleValue()
    }

    suspend fun deletePlayerDuplicate(playerId: String) {
        dslContext
            .deleteFrom(REFERENCE_PLAYER_DUPLICATE)
            .where(REFERENCE_PLAYER_DUPLICATE.PLAYER_ID.eq(playerId))
            .awaitExecute()
    }

    suspend fun listAllPlayerDuplicates(): List<ReferencePlayerDuplicate> {
        return dslContext
            .select(REFERENCE_PLAYER_DUPLICATE)
            .from(REFERENCE_PLAYER_DUPLICATE)
            .awaitMappedRecords()
    }

    suspend fun listByMostNumberOfGames(limit: Int): List<NumberOfGamesRecord> {
        val gamesAsRed = DSL.`when`(REFERENCE_PLAYER.ID.eq(REFERENCE_GAME.RED_PLAYER), 1).otherwise(0)
        val gamesAsBlack = DSL.`when`(REFERENCE_PLAYER.ID.eq(REFERENCE_GAME.BLACK_PLAYER), 1).otherwise(0)
        val totalGames = DSL.sum(gamesAsRed).plus(DSL.sum(gamesAsBlack)).`as`("total_games")

        return dslContext
            .select(
                REFERENCE_PLAYER.ID,
                REFERENCE_PLAYER.SOURCE_NAME,
                REFERENCE_PLAYER.CANONICAL_NAME,
                REFERENCE_PLAYER.CHINESE_NAME,
                DSL.sum(gamesAsRed).`as`("games_as_red"),
                DSL.sum(gamesAsBlack).`as`("games_as_black"),
                totalGames
            )
            .from(REFERENCE_PLAYER)
            .join(REFERENCE_GAME)
            .on(
                REFERENCE_PLAYER.ID.eq(REFERENCE_GAME.RED_PLAYER)
                    .or(REFERENCE_PLAYER.ID.eq(REFERENCE_GAME.BLACK_PLAYER))
            )
            .groupBy(
                REFERENCE_PLAYER.ID,
                REFERENCE_PLAYER.SOURCE_NAME,
                REFERENCE_PLAYER.CANONICAL_NAME,
                REFERENCE_PLAYER.CHINESE_NAME
            )
            .orderBy(totalGames.desc())
            .limit(limit)
            .awaitRecords()
            .map { record ->
                NumberOfGamesRecord(
                    id = record.get(REFERENCE_PLAYER.ID),
                    sourceName = record.get(REFERENCE_PLAYER.SOURCE_NAME),
                    canonicalName = record.get(REFERENCE_PLAYER.CANONICAL_NAME),
                    chineseName = record.get(REFERENCE_PLAYER.CHINESE_NAME),
                    gamesAsRed = record.get("games_as_red", Int::class.java) ?: 0,
                    gamesAsBlack = record.get("games_as_black", Int::class.java) ?: 0,
                    totalGames = record.get("total_games", Int::class.java) ?: 0
                )
            }
    }

    suspend fun listPlayersByWins(
        limit: Int? = null,
        offset: Int? = null
    ): List<PlayerStandingsRecord> {
        fun outcomeField(
            playerField: TableField<ReferenceGameRecord, String>,
            outcome: Outcome
        ): AggregateFunction<BigDecimal> {
            val condition =
                REFERENCE_PLAYER.ID.eq(playerField)
                    .and(REFERENCE_GAME.OUTCOME.eq(outcome))

            return DSL.sum(DSL.`when`(condition, 1).otherwise(0))
        }

        // count wins
        val winsAsRed = outcomeField(REFERENCE_GAME.RED_PLAYER, RED_WINS)
        val winsAsBlack = outcomeField(REFERENCE_GAME.BLACK_PLAYER, BLACK_WINS)
        val totalWins = winsAsRed.plus(winsAsBlack).`as`("total_wins")

        // count draws (doesn't matter if player was red or black)
        val drawCondition = REFERENCE_GAME.OUTCOME.eq(DRAW)
        val drawsExpr = DSL.sum(DSL.`when`(drawCondition, 1).otherwise(0))
        val totalDraws = drawsExpr.`as`("total_draws")

        // count losses
        val lossesAsRed = outcomeField(REFERENCE_GAME.RED_PLAYER, BLACK_WINS)
        val lossesAsBlack = outcomeField(REFERENCE_GAME.BLACK_PLAYER, RED_WINS)
        val totalLosses = lossesAsRed.plus(lossesAsBlack).`as`("total_losses")

        // total games expression for HAVING clause (can't use aliases, must use the actual expressions)
        val totalGamesExpr = winsAsRed.plus(winsAsBlack)
                .plus(drawsExpr)
                .plus(lossesAsRed).plus(lossesAsBlack)

        val baseQuery = dslContext
            .select(
                REFERENCE_PLAYER.ID,
                REFERENCE_PLAYER.CANONICAL_NAME,
                REFERENCE_PLAYER.CHINESE_NAME,
                totalWins,
                totalDraws,
                totalLosses
            )
            .from(
                REFERENCE_PLAYER,
                REFERENCE_GAME
            )
            .where(
                REFERENCE_PLAYER.ID.eq(REFERENCE_GAME.RED_PLAYER)
                    .or(REFERENCE_PLAYER.ID.eq(REFERENCE_GAME.BLACK_PLAYER))
            )
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .groupBy(
                REFERENCE_PLAYER.ID,
                REFERENCE_PLAYER.CANONICAL_NAME,
                REFERENCE_PLAYER.CHINESE_NAME
            )
            .having(totalGamesExpr.greaterOrEqual(BigDecimal(5)))
            .orderBy(DSL.field("total_wins").desc())
            .run {
                when {
                    limit != null && offset != null -> this.limit(limit).offset(offset)
                    limit != null -> this.limit(limit)
                    offset != null -> this.offset(offset)
                    else -> this
                }
            }

        return baseQuery
            .awaitRecords()
            .map { record ->
                val canonicalName = record.get(REFERENCE_PLAYER.CANONICAL_NAME)
                val wins = record.get("total_wins", Int::class.java) ?: 0
                val draws = record.get("total_draws", Int::class.java) ?: 0
                val losses = record.get("total_losses", Int::class.java) ?: 0

                PlayerStandingsRecord(
                    playerId = record.get(REFERENCE_PLAYER.ID),
                    canonicalName = canonicalName,
                    chineseName = record.get(REFERENCE_PLAYER.CHINESE_NAME),
                    slug = canonicalName.replace(" ", "_"),
                    wins = wins,
                    draws = draws,
                    losses = losses,
                    totalGames = wins + draws + losses
                )
            }
    }

    /**
     * Doesn't include profile text
     */
    suspend fun fetchEditHistory(playerId: String): List<ReferencePlayerProfileEdit> {
        return dslContext
            .select(
                REFERENCE_PLAYER_PROFILE_EDIT.VERSION,
                REFERENCE_PLAYER_PROFILE_EDIT.EDITOR_ID,
                REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME,
                REFERENCE_PLAYER_PROFILE_EDIT.COMMENT,
                REFERENCE_PLAYER_PROFILE_EDIT.CANONICAL_NAME,
                REFERENCE_PLAYER_PROFILE_EDIT.CHINESE_NAME,
                REFERENCE_PLAYER_PROFILE_EDIT.GENDER,
                REFERENCE_PLAYER_PROFILE_EDIT.ENABLED
            )
            .from(REFERENCE_PLAYER_PROFILE_EDIT)
            .where(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID.eq(playerId))
            .orderBy(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME.desc())
            .awaitMappedRecords()
    }

    suspend fun findLatestEnabledProfileEdit(playerId: String): ReferencePlayerProfileEdit? {
        return dslContext
            .select(REFERENCE_PLAYER_PROFILE_EDIT)
            .from(REFERENCE_PLAYER, REFERENCE_PLAYER_PROFILE_EDIT)
            .where(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID.eq(playerId))
            .and(REFERENCE_PLAYER_PROFILE_EDIT.ENABLED.eq(true))
            .orderBy(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME.desc())
            .limit(1)
            .awaitSingleMappedRecord()
    }

    suspend fun findLatestEditTimes(playerIds: List<String>): Map<String, Instant> {
        if (playerIds.isEmpty()) return emptyMap()

        return dslContext
            .select(
                REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID,
                DSL.max(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME)
            )
            .from(REFERENCE_PLAYER_PROFILE_EDIT)
            .where(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID.`in`(playerIds))
            .groupBy(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID)
            .awaitRecords()
            .mapNotNull { record ->
                val playerId = record.get(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID)
                val maxTime = record.get(1, Instant::class.java)
                if (playerId != null && maxTime != null) {
                    playerId to maxTime
                } else {
                    null
                }
            }
            .toMap()
    }

    suspend fun findProfileEdit(playerId: String, version: Int): ReferencePlayerProfileEdit? {
        return dslContext
            .select(REFERENCE_PLAYER_PROFILE_EDIT)
            .from(REFERENCE_PLAYER, REFERENCE_PLAYER_PROFILE_EDIT)
            .where(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID.eq(playerId))
            .and(REFERENCE_PLAYER_PROFILE_EDIT.VERSION.eq(version))
            .orderBy(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME.desc())
            .limit(1)
            .awaitSingleMappedRecord()
    }

    suspend fun findLatestEnabledProfileSources(playerId: String): List<ReferencePlayerProfileEditSource> {
        return dslContext
            .select(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE)
            .from(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE)
            .where(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.PLAYER_ID.eq(playerId))
            .and(
                REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.VERSION.eq(
                    dslContext
                        .select(REFERENCE_PLAYER_PROFILE_EDIT.VERSION)
                        .from(REFERENCE_PLAYER_PROFILE_EDIT)
                        .where(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID.eq(playerId))
                        .and(REFERENCE_PLAYER_PROFILE_EDIT.ENABLED.eq(true))
                        .orderBy(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME.desc())
                        .limit(1)
                )
            )
            .orderBy(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.SOURCE_INDEX.asc())
            .awaitMappedRecords()
    }

    suspend fun findProfileSourcesByVersion(
        playerId: String,
        versionIndex: Int
    ): List<ReferencePlayerProfileEditSource> {
        return dslContext
            .select()
            .from(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE)
            .where(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.PLAYER_ID.eq(playerId))
            .and(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.VERSION.eq(versionIndex))
            .orderBy(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.SOURCE_INDEX.asc())
            .awaitMappedRecords()
    }

    /**
     * Only from enabled edits
     */
    suspend fun listAllEditorsUserId(playerId: String): List<String> {
        return dslContext
            .selectDistinct(REFERENCE_PLAYER_PROFILE_EDIT.EDITOR_ID)
            .from(REFERENCE_PLAYER_PROFILE_EDIT)
            .where(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID.eq(playerId))
            .and(REFERENCE_PLAYER_PROFILE_EDIT.ENABLED.eq(true))
            .awaitRecords()
            .map { record -> record.get(REFERENCE_PLAYER_PROFILE_EDIT.EDITOR_ID) }
    }

    suspend fun fetchGameStats(playerId: String): PlayerGameStatsRecord {
        return fetchGameStats(listOf(playerId))
    }

    suspend fun fetchGameStats(playerIds: List<String>): PlayerGameStatsRecord {
        fun createPlayerOutcomeSum(
            playerIds: List<String>,
            playerField: TableField<*, String>,
            outcome: Outcome
        ) = DSL.sum(DSL.`when`(playerField.`in`(playerIds).and(REFERENCE_GAME.OUTCOME.eq(outcome)), 1).otherwise(0))

        if (playerIds.isEmpty()) {
            return PlayerGameStatsRecord(0, 0, 0, 0, 0, 0)
        }

        val records = dslContext
            .select(
                createPlayerOutcomeSum(playerIds, REFERENCE_GAME.RED_PLAYER, RED_WINS),
                createPlayerOutcomeSum(playerIds, REFERENCE_GAME.RED_PLAYER, BLACK_WINS),
                createPlayerOutcomeSum(playerIds, REFERENCE_GAME.RED_PLAYER, DRAW),
                createPlayerOutcomeSum(playerIds, REFERENCE_GAME.BLACK_PLAYER, BLACK_WINS),
                createPlayerOutcomeSum(playerIds, REFERENCE_GAME.BLACK_PLAYER, RED_WINS),
                createPlayerOutcomeSum(playerIds, REFERENCE_GAME.BLACK_PLAYER, DRAW)
            )
            .from(REFERENCE_GAME)
            .where(
                REFERENCE_GAME.RED_PLAYER.`in`(playerIds)
                    .or(REFERENCE_GAME.BLACK_PLAYER.`in`(playerIds))
            )
            .awaitRecords()

        // Aggregate queries always return at least one row, even if no matches
        val record = records.firstOrNull() ?: return PlayerGameStatsRecord(
            redWins = 0,
            redLosses = 0,
            redDraws = 0,
            blackWins = 0,
            blackLosses = 0,
            blackDraws = 0
        )

        return PlayerGameStatsRecord(
            redWins = (record.value1() as? Number)?.toInt() ?: 0,
            redLosses = (record.value2() as? Number)?.toInt() ?: 0,
            redDraws = (record.value3() as? Number)?.toInt() ?: 0,
            blackWins = (record.value4() as? Number)?.toInt() ?: 0,
            blackLosses = (record.value5() as? Number)?.toInt() ?: 0,
            blackDraws = (record.value6() as? Number)?.toInt() ?: 0
        )
    }

    suspend fun listAllPlayers(): List<ReferencePlayerRecord> {
        return dslContext
            .select()
            .from(REFERENCE_PLAYER)
            .orderBy(REFERENCE_PLAYER.SOURCE_NAME)
            .awaitMappedRecords()
    }

    suspend fun listPlayersStartingWith(startsWith: String, limit: Int): List<EntityIdAndNameRecord> {
        return dslContext
            .select(
                REFERENCE_PLAYER.ID,
                REFERENCE_PLAYER.CANONICAL_NAME,
                REFERENCE_PLAYER.CHINESE_NAME
            )
            .from(REFERENCE_PLAYER)
            .where(lower(REFERENCE_PLAYER.CANONICAL_NAME).startsWith(startsWith.lowercase()))
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .orderBy(REFERENCE_PLAYER.CANONICAL_NAME)
            .limit(limit)
            .awaitRecords()
            .map { record -> mapToEntityIdAndNameRecord(record) }
            .toList()
    }

    suspend fun listPlayersContaining(contains: String, limit: Int): List<EntityIdAndNameRecord> {
        return dslContext
            .select(
                REFERENCE_PLAYER.ID,
                REFERENCE_PLAYER.CANONICAL_NAME,
                REFERENCE_PLAYER.CHINESE_NAME
            )
            .from(REFERENCE_PLAYER)
            .where(lower(REFERENCE_PLAYER.CANONICAL_NAME).contains(contains.lowercase()))
            .and(REFERENCE_PLAYER.IS_VISIBLE.eq(true))
            .orderBy(REFERENCE_PLAYER.CANONICAL_NAME)
            .limit(limit)
            .awaitRecords()
            .map { record -> mapToEntityIdAndNameRecord(record) }
            .toList()
    }

    suspend fun findLatestProfileVersions(limit: Int): List<ReferencePlayerProfileEdit> {
        return dslContext
            .select(REFERENCE_PLAYER_PROFILE_EDIT)
            .from(REFERENCE_PLAYER_PROFILE_EDIT)
            .orderBy(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    suspend fun findMostEditedPlayers(limit: Int): List<EntityIdAndNameRecord> {
        return dslContext
            .selectDistinct(
                REFERENCE_PLAYER_PROFILE_EDIT.CANONICAL_NAME,
                REFERENCE_PLAYER_PROFILE_EDIT.CHINESE_NAME,
                DSL.count(),
                DSL.max(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME)
            )
            .from(REFERENCE_PLAYER_PROFILE_EDIT)
            .where(REFERENCE_PLAYER_PROFILE_EDIT.ENABLED.eq(true))
            .groupBy(
                REFERENCE_PLAYER_PROFILE_EDIT.CANONICAL_NAME,
                REFERENCE_PLAYER_PROFILE_EDIT.CHINESE_NAME
            )
            .orderBy(
                DSL.count(),
                DSL.max(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME).desc()
            )
            .limit(limit)
            .awaitRecords()
            .map { record ->
                mapToEntityIdAndNameRecord(record, REFERENCE_PLAYER_PROFILE_EDIT.CANONICAL_NAME)
            }
    }

    suspend fun createNewProfileVersion(
        playerId: String,
        canonicalName: String,
        chineseName: String?,
        gender: String?,
        profileText: String,
        sources: List<ReferencePlayerProfileEditSource>,
        editorId: String,
        comment: String
    ) {
        dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)

            // Get the next version number
            val currentVersion: Int = transactional
                .select(DSL.max(REFERENCE_PLAYER_PROFILE_EDIT.VERSION))
                .from(REFERENCE_PLAYER_PROFILE_EDIT)
                .where(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID.eq(playerId))
                .awaitSingleValue() ?: -1 // if no version exist yet, we start at -1 so it increments to 0

            val nextVersion = (currentVersion) + 1

            val now = Clock.System.now()

            // update the reference_player table
            transactional
                .update(REFERENCE_PLAYER)
                .set(REFERENCE_PLAYER.CANONICAL_NAME, canonicalName)
                .set(REFERENCE_PLAYER.CHINESE_NAME, chineseName)
                .set(REFERENCE_PLAYER.GENDER, gender)
                .where(REFERENCE_PLAYER.ID.eq(playerId))
                .awaitExecute()

            // insert the new profile version
            transactional
                .insertInto(REFERENCE_PLAYER_PROFILE_EDIT)
                .set(REFERENCE_PLAYER_PROFILE_EDIT.PLAYER_ID, playerId)
                .set(REFERENCE_PLAYER_PROFILE_EDIT.VERSION, nextVersion)
                .set(REFERENCE_PLAYER_PROFILE_EDIT.PROFILE, profileText)
                .set(REFERENCE_PLAYER_PROFILE_EDIT.CANONICAL_NAME, canonicalName)
                .set(REFERENCE_PLAYER_PROFILE_EDIT.CHINESE_NAME, chineseName)
                .set(REFERENCE_PLAYER_PROFILE_EDIT.GENDER, gender)
                .set(REFERENCE_PLAYER_PROFILE_EDIT.VERSION_TIME, now)
                .set(REFERENCE_PLAYER_PROFILE_EDIT.EDITOR_ID, editorId)
                .set(REFERENCE_PLAYER_PROFILE_EDIT.COMMENT, comment)
                .awaitExecute()

            // insert the sources
            sources.forEach { source ->
                transactional
                    .insertInto(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE)
                    .set(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.PLAYER_ID, playerId)
                    .set(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.VERSION, nextVersion)
                    .set(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.SOURCE_INDEX, source.sourceIndex)
                    .set(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.URL, source.url)
                    .set(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.TITLE, source.title)
                    .set(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.EDITOR_ID, editorId)
                    .awaitExecute()
            }
        }
    }

    private companion object {

        fun mapToEntityIdAndNameRecord(
            record: Record,
            idField: TableField<*, String> = REFERENCE_PLAYER.ID
        ): EntityIdAndNameRecord {
            return EntityIdAndNameRecord(
                id = record.get(idField),
                name = formatWithChineseName(
                    record.get(REFERENCE_PLAYER.CANONICAL_NAME),
                    record.get(REFERENCE_PLAYER.CHINESE_NAME)
                )
            )
        }

    }

}
