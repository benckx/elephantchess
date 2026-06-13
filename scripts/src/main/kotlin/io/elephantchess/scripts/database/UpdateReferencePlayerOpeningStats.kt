package io.elephantchess.scripts.database

import io.elephantchess.config.ArgConfig
import io.elephantchess.config.loadAppConfig
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_PLAYER_OPENING_PRE_CALCULATION_CACHE
import io.elephantchess.db.dao.codegen.tables.daos.ReferencePlayerOpeningPreCalculationCacheDao
import io.elephantchess.db.dao.codegen.tables.pojos.ReferencePlayerOpeningPreCalculationCache
import io.elephantchess.db.services.OpeningRepositoryCacheDaoService.Companion.movesToKey
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.utils.*
import io.elephantchess.model.Outcome
import io.elephantchess.model.Outcome.*
import io.elephantchess.scripts.utils.ScriptConfig.Companion.loadScriptConfig
import io.elephantchess.scripts.utils.getScriptDslContext
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.coroutines.runBlocking
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock

private val logger = logger {}

private const val READ_PROFILE = "local-backup"
private const val WRITE_PROFILE = "local-backup"
private const val MAX_MOVES = 48
private const val MIN_OCCURRENCES = 2

// Building the per-player repertoire for every player would create far too many rows, so we limit
// it to players that have enough games to make the stats meaningful.
private const val MIN_GAMES_PER_PLAYER = 250

private val scriptConfig = loadScriptConfig()
private val readAppConfig = loadAppConfig(ArgConfig(READ_PROFILE, scriptConfig.configurationLocation))
private val writeAppConfig = loadAppConfig(ArgConfig(WRITE_PROFILE, scriptConfig.configurationLocation))
private val readContext = getScriptDslContext(readAppConfig, maximumPoolSize = 8)
private val writeContext = getScriptDslContext(writeAppConfig, maximumPoolSize = 2)
private val referenceGameDaoService = ReferenceGameDaoService(readContext)

fun main(): Unit = runBlocking {
    val start = System.currentTimeMillis()

    val games = mutableListOf<ReferencePlayerGameDto>()
    listAllGames().forEachIndexed { i, game ->
        val moves = referenceGameDaoService.listMoves(game.id)
        if (moves.isNotEmpty()) {
            games += game.copy(moves = moves)
            if (i % 1_000 == 0) {
                logger.info { "loaded $i games into memory" }
            }
        }
    }

    val gamesByPlayer = mutableMapOf<String, MutableList<ReferencePlayerGameDto>>()
    games.forEach { game ->
        game.redPlayer?.let { gamesByPlayer.getOrPut(it) { mutableListOf() } += game }
        game.blackPlayer?.let { gamesByPlayer.getOrPut(it) { mutableListOf() } += game }
    }

    val qualifyingPlayers = gamesByPlayer.filterValues { it.size >= MIN_GAMES_PER_PLAYER }
    logger.info { "${qualifyingPlayers.size} players with >= $MIN_GAMES_PER_PLAYER games" }

    qualifyingPlayers.forEach { (playerId, playerGames) ->
        Color.entries.forEach { color ->
            val gamesAsColor = playerGames.filter { game -> game.playerColor(playerId) == color }
            val records = buildRepertoire(playerId, color, gamesAsColor)
            persistRepertoire(playerId, color, records)
            logger.info { "player $playerId as $color: ${gamesAsColor.size} games -> ${records.size} opening entries" }
        }
    }

    logger.info { "done in ${(System.currentTimeMillis() - start) / 60_000} minutes" }
}

/**
 * Aggregate the opening stats (occurrences and outcomes) of every move prefix the player reached
 * while playing the given color, keeping only prefixes that occurred at least [MIN_OCCURRENCES]
 * times to limit the number of rows.
 */
private fun buildRepertoire(
    playerId: String,
    color: Color,
    games: List<ReferencePlayerGameDto>
): List<ReferencePlayerOpeningPreCalculationCache> {
    val aggregates = mutableMapOf<String, OpeningAggregate>()

    games.forEach { game ->
        val maxPrefix = minOf(MAX_MOVES, game.moves.size)
        for (length in 1..maxPrefix) {
            val key = movesToKey(game.moves.take(length))
            val aggregate = aggregates.getOrPut(key) { OpeningAggregate(length) }
            aggregate.register(game.outcome)
        }
    }

    val now = Clock.System.now()
    return aggregates
        .filterValues { it.occurrences >= MIN_OCCURRENCES }
        .map { (movesKey, aggregate) ->
            val record = ReferencePlayerOpeningPreCalculationCache()
            record.referencePlayerId = playerId
            record.color = color.name
            record.numberOfMoves = aggregate.numberOfMoves
            record.moves = movesKey
            record.occurrences = aggregate.occurrences
            record.outcomeRedWins = aggregate.redWins
            record.outcomeBlackWins = aggregate.blackWins
            record.outcomeDraws = aggregate.draws
            record.entryCreation = now
            record.entryUpdate = now
            record
        }
}

private suspend fun persistRepertoire(
    playerId: String,
    color: Color,
    records: List<ReferencePlayerOpeningPreCalculationCache>
) {
    writeContext.transactionCoroutine { cfg ->
        DSL.using(cfg)
            .deleteFrom(REFERENCE_PLAYER_OPENING_PRE_CALCULATION_CACHE)
            .where(REFERENCE_PLAYER_OPENING_PRE_CALCULATION_CACHE.REFERENCE_PLAYER_ID.eq(playerId))
            .and(REFERENCE_PLAYER_OPENING_PRE_CALCULATION_CACHE.COLOR.eq(color.name))
            .awaitExecute()

        ReferencePlayerOpeningPreCalculationCacheDao(cfg).insertMultipleReactive(records)
    }
}

private suspend fun listAllGames(): List<ReferencePlayerGameDto> {
    return readContext
        .select(
            REFERENCE_GAME.ID,
            REFERENCE_GAME.OUTCOME,
            REFERENCE_GAME.RED_PLAYER,
            REFERENCE_GAME.BLACK_PLAYER
        )
        .from(REFERENCE_GAME)
        .awaitRecords()
        .map { record ->
            ReferencePlayerGameDto(
                id = record.get(REFERENCE_GAME.ID),
                outcome = record.get(REFERENCE_GAME.OUTCOME),
                redPlayer = record.get(REFERENCE_GAME.RED_PLAYER),
                blackPlayer = record.get(REFERENCE_GAME.BLACK_PLAYER),
                moves = emptyList()
            )
        }
        .toList()
}

private class OpeningAggregate(val numberOfMoves: Int) {
    var occurrences: Int = 0
    var redWins: Int = 0
    var blackWins: Int = 0
    var draws: Int = 0

    fun register(outcome: Outcome) {
        occurrences++
        when (outcome) {
            RED_WINS -> redWins++
            BLACK_WINS -> blackWins++
            DRAW -> draws++
        }
    }
}

private data class ReferencePlayerGameDto(
    val id: String,
    val outcome: Outcome,
    val redPlayer: String?,
    val blackPlayer: String?,
    val moves: List<String>
) {
    fun playerColor(playerId: String): Color? {
        return when (playerId) {
            redPlayer -> RED
            blackPlayer -> BLACK
            else -> null
        }
    }
}
