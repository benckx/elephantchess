package io.elephantchess.scripts.database

import io.elephantchess.config.ArgConfig
import io.elephantchess.config.loadAppConfig
import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.OpeningPreCalculationCacheDao
import io.elephantchess.db.dao.codegen.tables.daos.OpeningPreCalculationCacheReferenceGameDao
import io.elephantchess.db.dao.codegen.tables.pojos.OpeningPreCalculationCache
import io.elephantchess.db.dao.codegen.tables.pojos.OpeningPreCalculationCacheReferenceGame
import io.elephantchess.db.services.OpeningRepositoryCacheDaoService.Companion.movesToKey
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.utils.*
import io.elephantchess.model.Outcome
import io.elephantchess.model.Outcome.*
import io.elephantchess.scripts.utils.ScriptConfig.Companion.loadScriptConfig
import io.elephantchess.scripts.utils.getScriptDslContext
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.HalfMove
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import kotlinx.coroutines.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val logger = logger {}

private const val READ_PROFILE = "local-backup"
private const val WRITE_PROFILE = "local-backup"
private const val MAX_MOVES = 48
private const val MIN_OCCURRENCES = 2

private const val STOP_TIMEOUT = 60_000L

private val scriptConfig = loadScriptConfig()
private val readAppConfig = loadAppConfig(ArgConfig(READ_PROFILE, scriptConfig.configurationLocation))
private val writeAppConfig = loadAppConfig(ArgConfig(WRITE_PROFILE, scriptConfig.configurationLocation))
private val readContext = getScriptDslContext(readAppConfig, maximumPoolSize = 8)
private val writeContext = getScriptDslContext(writeAppConfig, maximumPoolSize = 2)
private val referenceGameDaoService = ReferenceGameDaoService(readContext)
private var memoryCache = mutableListOf<ReferenceGameDto>()

private val lastActivityTime = AtomicLong(System.currentTimeMillis())
private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(8))

private val countUpdates = AtomicInteger(0)
private val countInserts = AtomicInteger(0)

fun main(): Unit = runBlocking {
    val start = System.currentTimeMillis()

    listAllGames().forEachIndexed { i, (id, outcome) ->
        val moves = referenceGameDaoService.listMoves(id)
        val gameDto = ReferenceGameDto(id, outcome, moves)
        if (gameDto.moves.isNotEmpty()) {
            memoryCache += gameDto
            if (i % 1_000 == 0) {
                logger.info { "loaded $i games into memory cache" }
            }
        }
    }

    scope.launch {
        refreshCache(listOf())
    }

    // wait until no refreshCache activity for more than 1 minute before quitting
    while (true) {
        delay(10.seconds)
        val timeSinceLastActivity = System.currentTimeMillis() - lastActivityTime.get()
        if (timeSinceLastActivity > STOP_TIMEOUT) {
            logger.info { "no activity for more than $STOP_TIMEOUT ms, shutting down" }
            break
        }
    }
    logger.info { "done in ${(System.currentTimeMillis() - start) / 60_000} minutes" }
    logger.info { "updates: ${countUpdates.get()}, insertions: ${countInserts.get()}" }
}

private fun listNextMovesAndCount(moves: List<String>): List<NextMoveOccurrenceCount> {
    val subset = if (moves.isEmpty()) {
        memoryCache
    } else {
        memoryCache.filter { dto -> dto.moves.take(moves.size) == moves && dto.moves.size > moves.size }
    }

    return subset.map { dto -> dto.moves[moves.size] }.distinct().map { nextMove ->
        val sequence = moves + nextMove
        NextMoveOccurrenceCount(nextMove, subset.count { dto -> dto.moves.take(sequence.size) == sequence })
    }
}

private fun countByOutcome(moves: List<String>): Map<Outcome, Int> {
    val subset = memoryCache.filter { dto -> dto.moves.take(moves.size) == moves }
    val redWins = subset.count { dto -> dto.outcome == RED_WINS }
    val blackWins = subset.count { dto -> dto.outcome == BLACK_WINS }
    val draws = subset.count { dto -> dto.outcome == DRAW }
    return mapOf(RED_WINS to redWins, BLACK_WINS to blackWins, DRAW to draws)
}

private fun listMatchingGameIds(moves: List<String>): List<String> {
    return memoryCache
        .filter { dto -> dto.moves.take(moves.size) == moves }
        .map { dto -> dto.id }
}

private suspend fun refreshCache(moves: List<String>) {
    lastActivityTime.set(System.currentTimeMillis())
    if (moves.size <= MAX_MOVES) {
        logger.debug { "refreshing cache for ${moves.joinToString(", ")}" }
        listNextMovesAndCount(moves).forEach { record ->
            val nextMove = record.nextMove
            val occurrences = record.occurrences
            val nextSequenceOfMoves = moves + nextMove
            val movesKey = movesToKey(nextSequenceOfMoves)
            val cacheEntryId = findEntryId(nextSequenceOfMoves)
            val alreadyExists = cacheEntryId != null

            if (isValidSequenceOfMoves(nextSequenceOfMoves)) {
                val outcome = countByOutcome(nextSequenceOfMoves)
                val openingCacheRecord = OpeningPreCalculationCache()
                openingCacheRecord.numberOfMoves = nextSequenceOfMoves.size
                openingCacheRecord.moves = movesKey
                openingCacheRecord.occurrences = occurrences
                openingCacheRecord.outcomeRedWins = outcome[RED_WINS] ?: 0
                openingCacheRecord.outcomeBlackWins = outcome[BLACK_WINS] ?: 0
                openingCacheRecord.outcomeDraws = outcome[DRAW] ?: 0

                val now = Clock.System.now()
                openingCacheRecord.entryCreation = now
                openingCacheRecord.entryUpdate = now

                val matchingGameIds = listMatchingGameIds(nextSequenceOfMoves)

                writeContext.transactionCoroutine { cfg ->
                    val transaction = DSL.using(cfg)

                    val entryId = if (alreadyExists) {
                        // update
                        updateCache(transaction, cacheEntryId, openingCacheRecord)
                        if (countUpdates.incrementAndGet() % 20 == 0) {
                            logger.info { "[${nextSequenceOfMoves.size}] updated cache for $movesKey (total ${countUpdates.get()})" }
                        }
                        cacheEntryId
                    } else {
                        // insert
                        OpeningPreCalculationCacheDao(cfg).insertReactive(openingCacheRecord)
                        if (countInserts.incrementAndGet() % 20 == 0) {
                            logger.info { "[${nextSequenceOfMoves.size}] inserted cache for $movesKey (total ${countInserts.get()})" }
                        }
                        findEntryId(transaction, nextSequenceOfMoves)
                    }

                    if (entryId != null) {
                        refreshGameMapping(transaction, entryId, matchingGameIds)
                    }
                }

                if (occurrences >= MIN_OCCURRENCES) {
                    scope.launch {
                        refreshCache(nextSequenceOfMoves)
                    }
                }
            } else {
                logger.warn { "illegal sequence of moves: ${nextSequenceOfMoves.joinToString(", ")}" }
            }
        }
    }
}

private fun isValidSequenceOfMoves(moves: List<String>): Boolean {
    try {
        val board = Board()
        moves.forEach { move ->
            val halfMove = HalfMove.parseMoveFromUci(move)
            if (!board.isLegalMove(halfMove)) {
                return false
            }
            board.registerMove(halfMove)
        }
        return true
    } catch (e: Exception) {
        logger.error(e) { "error validating sequence of moves" }
        return false
    }
}

private suspend fun findEntryId(moves: List<String>): Int? {
    return findEntryId(writeContext, moves)
}

private suspend fun findEntryId(dslContext: DSLContext, moves: List<String>): Int? {
    return dslContext
        .select(OPENING_PRE_CALCULATION_CACHE.ID)
        .from(OPENING_PRE_CALCULATION_CACHE)
        .where(OPENING_PRE_CALCULATION_CACHE.NUMBER_OF_MOVES.eq(moves.size))
        .and(OPENING_PRE_CALCULATION_CACHE.MOVES.eq(movesToKey(moves)))
        .awaitSingleValue()
}

private suspend fun updateCache(transaction: DSLContext, id: Int, record: OpeningPreCalculationCache) {
    transaction
        .update(OPENING_PRE_CALCULATION_CACHE)
        .set(OPENING_PRE_CALCULATION_CACHE.OCCURRENCES.fixed(), record.occurrences)
        .set(OPENING_PRE_CALCULATION_CACHE.OUTCOME_RED_WINS.fixed(), record.outcomeRedWins)
        .set(OPENING_PRE_CALCULATION_CACHE.OUTCOME_BLACK_WINS.fixed(), record.outcomeBlackWins)
        .set(OPENING_PRE_CALCULATION_CACHE.OUTCOME_DRAWS.fixed(), record.outcomeDraws)
        .set(OPENING_PRE_CALCULATION_CACHE.ENTRY_UPDATE.fixed(), record.entryUpdate)
        .where(OPENING_PRE_CALCULATION_CACHE.ID.eq(id))
        .awaitExecute()
}

/**
 * Keep track of which reference games match a given opening (i.e. cache entry). We store the
 * auto-increment id of the opening cache entry (rather than the much wider sequence of moves) to
 * keep this Cartesian-product table as small as possible.
 */
private suspend fun refreshGameMapping(transaction: DSLContext, cacheEntryId: Int, gameIds: List<String>) {
    transaction
        .deleteFrom(OPENING_PRE_CALCULATION_CACHE_REFERENCE_GAME)
        .where(OPENING_PRE_CALCULATION_CACHE_REFERENCE_GAME.OPENING_PRE_CALCULATION_CACHE_ID.eq(cacheEntryId))
        .awaitExecute()

    val mappings = gameIds.map { gameId ->
        OpeningPreCalculationCacheReferenceGame(
            cacheEntryId,
            gameId
        )
    }

    OpeningPreCalculationCacheReferenceGameDao(transaction.configuration())
        .insertMultipleReactive(mappings)
}

private suspend fun listAllGames(): List<Pair<String, Outcome>> {
    return readContext
        .select(
            REFERENCE_GAME.ID,
            REFERENCE_GAME.OUTCOME
        )
        .from(REFERENCE_GAME)
        .awaitRecords()
        .map { record ->
            record.get(REFERENCE_GAME.ID) to record.get(REFERENCE_GAME.OUTCOME)
        }
        .toList()
}

private data class NextMoveOccurrenceCount(
    val nextMove: String,
    val occurrences: Int
)

private data class ReferenceGameDto(
    val id: String,
    val outcome: Outcome,
    val moves: List<String>
)
