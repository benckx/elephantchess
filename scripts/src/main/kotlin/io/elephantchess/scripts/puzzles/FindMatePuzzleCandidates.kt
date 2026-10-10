package io.elephantchess.scripts.puzzles

import io.elephantchess.db.dao.codegen.Tables.PUZZLE
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME
import io.elephantchess.db.dao.codegen.tables.pojos.ReferenceGame
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.engines.protocol.commands.LocalProcessLocator
import io.elephantchess.scripts.KoinScript
import io.elephantchess.scripts.puzzles.FindMatePuzzleCandidates.MAX_MATE
import io.elephantchess.scripts.puzzles.FindMatePuzzleCandidates.OUTPUT_FILE_NAME
import io.elephantchess.servicelayer.utils.ops.safeQueryForDepth
import io.elephantchess.xiangqi.Board
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

/**
 * Finds "mate in X" puzzle candidates among reference games that are not used by any puzzle yet.
 *
 * For every such game it loads the last position of the game ([ReferenceGame.finalFen]) and asks the
 * engine whether the side to move has a forced checkmate. When a forced mate in `1..`[MAX_MATE] is
 * found (and verified by replaying the engine line), the candidate is written to [OUTPUT_FILE_NAME].
 *
 * The CSV is then consumed by [CreatePuzzlesFromCsv] to create the actual puzzles.
 */
object FindMatePuzzleCandidates : KoinScript {

    private const val DEPTH = 30
    private const val MAX_MATE = 5
    private const val TIME_OUT = 60_000L
    private const val OUTPUT_FILE_NAME = "mate_puzzle_candidates.csv"

    private const val PROFILE = "local-backup"
    private const val ENGINE_THREADS = 48

    private val dslContext by inject<DSLContext>()
    private val enginesPool by inject<EnginePool>()

    init {
        val enginePool = EnginePool(
            configMap = mapOf(
                PikafishEngineId to EngineConfig(
                    version = "2023-03-05",
                    poolSize = 1,
                    numberOfThreads = ENGINE_THREADS
                )
            ),
            executor = Executors.newVirtualThreadPerTaskExecutor(),
            engineProcessLocator = LocalProcessLocator,
        )

        initKoin(
            appProfile = PROFILE,
            enginesPool = enginePool
        )
    }

    private suspend fun fetchReferenceGamesWithoutPuzzle(): List<ReferenceGame> =
        dslContext
            .selectFrom(REFERENCE_GAME)
            .where(REFERENCE_GAME.FINAL_FEN.isNotNull)
            .and(REFERENCE_GAME.SOURCE.isNotNull)
            .and(REFERENCE_GAME.SOURCE_ID.isNotNull)
            // the final position must still be playable (the game ended before mate, e.g. on resignation)
            .and(REFERENCE_GAME.IS_CHECKMATE.ne(true).or(REFERENCE_GAME.IS_CHECKMATE.isNull))
            .and(REFERENCE_GAME.IS_STALEMATE.ne(true).or(REFERENCE_GAME.IS_STALEMATE.isNull))
            .and(
                REFERENCE_GAME.SOURCE_ID.notIn(
                    dslContext.select(PUZZLE.REF_GAME_SOURCE_ID).from(PUZZLE)
                )
            )
            .awaitMappedRecords<ReferenceGame>()

    private suspend fun evaluate(game: ReferenceGame, finalFen: String): MatePuzzleCandidate? {
        val board = Board(finalFen)
        val playerColor = board.colorToPlay()
        if (board.isCheckmated() || board.isStalemated(playerColor)) {
            return null
        }

        val result = enginesPool.safeQueryForDepth(
            fen = finalFen,
            engineId = PikafishEngineId,
            depth = DEPTH,
            timeout = TIME_OUT
        ) ?: return null

        val mateLine = result.infoLines
            .filter { line -> line.mate != null }
            .maxByOrNull { line -> line.depth ?: 0 }
            ?: return null

        // a positive mate means the side to move delivers the mate
        val mate = mateLine.mate ?: return null
        if (mate <= 0 || mate > MAX_MATE) {
            return null
        }

        val solutionMoves = mateLine.pv
        if (solutionMoves.isEmpty()) {
            return null
        }

        // verify the engine line actually ends in checkmate before trusting it
        val replay = Board(finalFen)
        solutionMoves.forEach { move -> replay.registerMove(move) }
        if (!replay.isCheckmated()) {
            logger.warn { "engine line for ${game.id} does not end in checkmate, skipping" }
            return null
        }

        return MatePuzzleCandidate(
            refGameSource = game.source,
            refGameSourceId = game.sourceId,
            playerColor = playerColor,
            mate = mate,
            depth = mateLine.depth ?: DEPTH,
            startFen = finalFen,
            solutionMoves = solutionMoves
        )
    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val games = fetchReferenceGamesWithoutPuzzle()
        logger.info { "checking ${games.size} reference games without puzzles" }

        var found = 0
        File(OUTPUT_FILE_NAME).printWriter().use { writer ->
            writer.println(MatePuzzleCandidate.CSV_HEADER)

            games.forEachIndexed { index, game ->
                val finalFen = game.finalFen ?: return@forEachIndexed
                try {
                    val candidate = evaluate(game, finalFen)
                    if (candidate != null) {
                        writer.println(candidate.toCsvLine())
                        writer.flush()
                        found++
                        logger.info { "[${index + 1}/${games.size}] ${game.id}: mate in ${candidate.mate}" }
                    }
                } catch (e: Exception) {
                    logger.warn { "could not evaluate ${game.id} due to ${e::class.simpleName}: ${e.message}" }
                }
            }
        }

        logger.info { "found $found mate puzzle candidates, written to $OUTPUT_FILE_NAME" }
    }

}
