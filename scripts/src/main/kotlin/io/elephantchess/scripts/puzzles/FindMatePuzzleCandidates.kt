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
import java.io.FileWriter
import java.io.PrintWriter
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

    private const val DEPTH = 22
    private const val MAX_MATE = 8
    private const val MIN_LEGAL_MOVES = 2
    private const val TIME_OUT = 180_000L
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
                    dslContext
                        .select(PUZZLE.REF_GAME_SOURCE_ID)
                        .from(PUZZLE)
                )
            )
            .awaitMappedRecords<ReferenceGame>()

    private suspend fun evaluate(game: ReferenceGame, finalFen: String): MatePuzzleCandidate? {
        logger.info { "evaluating ${game.id} (fen=$finalFen)" }

        val board = Board(finalFen)
        val playerColor = board.colorToPlay()
        if (board.isCheckmated() || board.isStalemated(playerColor)) {
            logger.info { "${game.id}: final position is already checkmate/stalemate, skipping" }
            return null
        }

        logger.info { "${game.id}: querying engine at depth $DEPTH ($playerColor to play)" }
        val result = enginesPool.safeQueryForDepth(
            fen = finalFen,
            engineId = PikafishEngineId,
            depth = DEPTH,
            timeout = TIME_OUT
        )
        if (result == null) {
            logger.info { "${game.id}: engine returned no result (timeout or acquisition failure), skipping" }
            return null
        }

        val mateLine = result.infoLines
            .filter { line -> line.mate != null }
            .maxByOrNull { line -> line.depth ?: 0 }
        if (mateLine == null) {
            logger.info { "${game.id}: no forced mate found by engine, skipping" }
            return null
        }

        // a positive mate means the side to move delivers the mate
        val mate = mateLine.mate ?: return null
        if (mate <= 0 || mate > MAX_MATE) {
            logger.info { "${game.id}: mate in $mate out of accepted range (1..$MAX_MATE), skipping" }
            return null
        }
        logger.info { "${game.id}: engine reports mate in $mate at depth ${mateLine.depth}" }

        val solutionMoves = mateLine.pv
        if (solutionMoves.isEmpty()) {
            logger.info { "${game.id}: engine mate line has no principal variation, skipping" }
            return null
        }

        // verify the engine line actually ends in checkmate before trusting it
        val replay = Board(finalFen)
        solutionMoves.forEach { move -> replay.registerMove(move) }
        if (!replay.isCheckmated()) {
            logger.warn { "engine line for ${game.id} does not end in checkmate, skipping" }
            return null
        }
        logger.info { "${game.id}: engine line verified to end in checkmate (${solutionMoves.size} plies)" }

        // apply the same solvability rule as DisablePuzzlesWithoutEnoughMoves: the player must have
        // at least MIN_LEGAL_MOVES legal moves at every one of their turns
        if (!PuzzleSolvabilityValidator.hasEnoughMovesAtEachPlayerStep(
                startFen = finalFen,
                solutionMoves = solutionMoves,
                minLegalMoves = MIN_LEGAL_MOVES,
            )
        ) {
            logger.info { "candidate for ${game.id} is too constrained (< $MIN_LEGAL_MOVES moves at some step), skipping" }
            return null
        }
        logger.info { "${game.id}: passed solvability check, accepting as candidate" }

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
        // sort by reference game id so the run is deterministic and resumable
        val games = fetchReferenceGamesWithoutPuzzle().sortedBy { game -> game.id }
        logger.info { "checking ${games.size} reference games without puzzles" }

        val outputFile = File(OUTPUT_FILE_NAME)
        val resumeIndex = resumeIndex(outputFile, games)
        if (resumeIndex > 0) {
            logger.info { "resuming from index $resumeIndex (${games.size - resumeIndex} games left to check)" }
        }

        var found = 0
        val total = games.size
        val startedAt = System.currentTimeMillis()
        // append when resuming an existing file, otherwise start fresh with a header
        val isResuming = resumeIndex > 0 && outputFile.exists()
        PrintWriter(FileWriter(outputFile, isResuming)).use { writer ->
            if (!isResuming) {
                writer.println(MatePuzzleCandidate.CSV_HEADER)
                writer.flush()
            }

            games.drop(resumeIndex).forEachIndexed { offset, game ->
                val index = resumeIndex + offset
                val finalFen = game.finalFen ?: return@forEachIndexed
                val done = index + 1
                val percent = done * 100.0 / total
                val eta = formatEta(startedAt, offset + 1, total - resumeIndex)
                logger.info { "[$done/$total] (${"%.1f".format(percent)}%, ETA $eta) evaluating ${game.id}" }
                try {
                    val candidate = evaluate(game, finalFen)
                    if (candidate != null) {
                        writer.println(candidate.toCsvLine())
                        writer.flush()
                        found++
                        logger.info { "[$done/$total] ${game.id}: mate in ${candidate.mate} -> candidate (total found: $found)" }
                    }
                } catch (e: Exception) {
                    logger.warn { "could not evaluate ${game.id} due to ${e::class.simpleName}: ${e.message}" }
                }
            }
        }

        logger.info { "found $found mate puzzle candidates, written to $OUTPUT_FILE_NAME" }
    }

    /**
     * Returns the index in the (id-sorted) [games] list from which to resume, based on the last
     * candidate already written to [outputFile]. Everything up to and including the last matched game
     * is considered done. Returns 0 when there is nothing to resume from.
     */
    private fun resumeIndex(outputFile: File, games: List<ReferenceGame>): Int {
        if (!outputFile.exists()) return 0

        val lastCandidate = outputFile.useLines { lines ->
            lines
                .lastOrNull { line -> line.isNotBlank() && line != MatePuzzleCandidate.CSV_HEADER }
                ?.let { line -> runCatching { MatePuzzleCandidate.fromCsvLine(line) }.getOrNull() }
        } ?: return 0

        val lastIndex = games.indexOfLast { game ->
            game.source == lastCandidate.refGameSource && game.sourceId == lastCandidate.refGameSourceId
        }
        return if (lastIndex < 0) 0 else lastIndex + 1
    }

    private fun formatEta(startedAt: Long, done: Int, total: Int): String {
        if (done <= 0) return "unknown"
        val elapsed = System.currentTimeMillis() - startedAt
        val remainingMs = (elapsed.toDouble() / done * (total - done)).toLong()
        val seconds = remainingMs / 1000 % 60
        val minutes = remainingMs / 1000 / 60 % 60
        val hours = remainingMs / 1000 / 60 / 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

}
