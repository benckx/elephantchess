package io.elephantchess.scripts.openings

import io.elephantchess.config.ArgConfig
import io.elephantchess.config.loadAppConfig
import io.elephantchess.db.dao.codegen.Tables.OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER
import io.elephantchess.db.dao.codegen.tables.daos.OpeningPreCalculationCacheReferencePlayerDao
import io.elephantchess.db.dao.codegen.tables.pojos.OpeningPreCalculationCacheReferencePlayer
import io.elephantchess.db.services.OpeningRepositoryCacheDaoService.Companion.movesToKey
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.utils.*
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.model.Outcome
import io.elephantchess.model.Outcome.BLACK_WINS
import io.elephantchess.model.Outcome.DRAW
import io.elephantchess.model.Outcome.RED_WINS
import io.elephantchess.scripts.utils.ScriptConfig.Companion.loadScriptConfig
import io.elephantchess.scripts.utils.getScriptDslContext
import io.elephantchess.xiangqi.Color
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.coroutines.runBlocking
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock

private val logger = logger {}

private const val READ_PROFILE = "local-backup"
private const val WRITE_PROFILE = "prod"
private const val MAX_MOVES = 48
private const val MIN_OCCURRENCES = 2
private const val MIN_GAMES_PER_PLAYER = 100

private val scriptConfig = loadScriptConfig()
private val readAppConfig = loadAppConfig(ArgConfig(READ_PROFILE, scriptConfig.configurationLocation))
private val writeAppConfig = loadAppConfig(ArgConfig(WRITE_PROFILE, scriptConfig.configurationLocation))
private val readContext = getScriptDslContext(readAppConfig, maximumPoolSize = 8)
private val writeContext = getScriptDslContext(writeAppConfig, maximumPoolSize = 2)
private val referenceGameDaoService = ReferenceGameDaoService(readContext)

fun main(): Unit = runBlocking {
    val start = System.currentTimeMillis()
    val memoryCache = loadGamesWithMoves(readContext, referenceGameDaoService) { i ->
        logger.info { "loaded $i games into memory cache" }
    }

    updatePlayerOpenings(memoryCache)

    logger.info { "done in ${(System.currentTimeMillis() - start) / 60_000} minutes" }
}

/**
 * (Re)build the per-reference-player opening repertoires, split by the color the player played,
 * restricted to players with at least [MIN_GAMES_PER_PLAYER] games.
 */
private suspend fun updatePlayerOpenings(memoryCache: List<ReferenceGameDto>) {
    val gamesByPlayer = mutableMapOf<String, MutableList<ReferenceGameDto>>()
    memoryCache.forEach { game ->
        game.redPlayer?.let { gamesByPlayer.getOrPut(it) { mutableListOf() } += game }
        game.blackPlayer?.let { gamesByPlayer.getOrPut(it) { mutableListOf() } += game }
    }

    val qualifyingPlayers = gamesByPlayer
        .filterValues { it.size >= MIN_GAMES_PER_PLAYER }
        .toList()
        .sortedByDescending { it.second.size }

    logger.info { "${qualifyingPlayers.size} players with >= $MIN_GAMES_PER_PLAYER games" }

    qualifyingPlayers.forEach { (playerId, playerGames) ->
        Color.entries.forEach { color ->
            val gamesAsColor = playerGames.filter { game -> game.playerColor(playerId) == color }
            val records = buildRepertoire(playerId, color, gamesAsColor)
            persistRepertoire(playerId, color, records)
            logger.info { "player $playerId as $color: ${gamesAsColor.size} games -> ${records.size} opening entries" }
        }
    }
}

/**
 * Aggregate the opening stats (occurrences and outcomes) of every move prefix the player reached
 * while playing the given color, keeping only prefixes that occurred at least [MIN_OCCURRENCES]
 * times to limit the number of rows.
 */
private fun buildRepertoire(
    playerId: String,
    color: Color,
    games: List<ReferenceGameDto>
): List<OpeningPreCalculationCacheReferencePlayer> {
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
            val record = OpeningPreCalculationCacheReferencePlayer()
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
    records: List<OpeningPreCalculationCacheReferencePlayer>
) {
    writeContext.transactionCoroutine { cfg ->
        DSL.using(cfg)
            .deleteFrom(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER)
            .where(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.REFERENCE_PLAYER_ID.eq(playerId))
            .and(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.COLOR.eq(color.name))
            .awaitExecute()
    }

    // Batch inserts in chunks of 500 to avoid memory issues
    records.chunked(500).forEach { chunkOfRecords ->
        writeContext.transactionCoroutine { cfg ->
            OpeningPreCalculationCacheReferencePlayerDao(cfg)
                .insertMultipleReactive(chunkOfRecords)
        }
    }
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
