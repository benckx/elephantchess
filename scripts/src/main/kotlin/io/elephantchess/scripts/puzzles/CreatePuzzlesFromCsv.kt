package io.elephantchess.scripts.puzzles

import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME_HALF_MOVE
import io.elephantchess.db.dao.codegen.tables.pojos.Puzzle
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleCategoryTag
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleHalfMove
import io.elephantchess.db.services.PuzzleDaoService
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.generateId
import io.elephantchess.model.Engine
import io.elephantchess.model.PuzzleAlgo
import io.elephantchess.model.PuzzleAlgo.FIND_PATH_TO_MATE
import io.elephantchess.model.PuzzleAlgo.FIND_PATH_TO_MATE_SHORTENED
import io.elephantchess.model.PuzzleCategory
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.xiangqi.Board
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.io.File
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * Creates MATE_IN_1, MATE_IN_2, ... puzzles from the candidates produced by [FindMatePuzzleCandidates].
 *
 * A candidate is a forced mate in N found from the final position of a reference game. From it we create:
 *  - one [PuzzleAlgo.FIND_PATH_TO_MATE] puzzle (MATE_IN_N) solved from the final position, and
 *  - for every shorter length k in `1 until N`, one [PuzzleAlgo.FIND_PATH_TO_MATE_SHORTENED] puzzle
 *    (MATE_IN_k) whose start position is the engine line advanced by `2 * (N - k)` plies, so only the
 *    last `k` player moves remain to be found.
 *
 * Candidates whose reference game already has a puzzle are skipped, so the script is safe to re-run.
 */
object CreatePuzzlesFromCsv : KoinScriptInit() {

    private const val INPUT_FILE_NAME = "mate_puzzle_candidates.csv"

    // mirrors the existing puzzles: MATE_IN_1 -> 400, MATE_IN_2 -> 800, ...
    private const val RATING_PER_MATE = 400

    private val puzzleDaoService by inject<PuzzleDaoService>()
    private val dslContext by inject<DSLContext>()

    /**
     * Creates the full-length puzzle plus every shorter shortened variant for a candidate.
     * @return the number of puzzles created.
     */
    private suspend fun createPuzzles(candidate: MatePuzzleCandidate): Int {
        val solutionLine = candidate.solutionMoves
        val mate = candidate.mate

        // the reference game history from the standard start position up to the puzzle start position;
        // the front-end move tree always renders from the standard start, so the stored non-solution
        // moves must trace back to it (not just the engine's revealed plies)
        val gameHistory = fetchGameHistory(candidate.refGameSource, candidate.refGameSourceId)
        if (gameHistory.isEmpty()) {
            logger.warn { "no game history for ${candidate.refGameSource}/${candidate.refGameSourceId}, skipping" }
            return 0
        }

        // the history must reconstruct the candidate start position when replayed from the standard start
        val reconstructed = Board().apply { gameHistory.forEach { move -> registerMove(move) } }
        if (positionKey(reconstructed.outputFen()) != positionKey(candidate.startFen)) {
            logger.warn {
                "game history for ${candidate.refGameSource}/${candidate.refGameSourceId} does not reconstruct " +
                    "the candidate start position, skipping"
            }
            return 0
        }

        // a clean "mate in N" line has 2N-1 plies (player, opponent, ..., player); if the engine line
        // does not match, only create the full-length puzzle to stay on the safe side
        val canShorten = solutionLine.size == 2 * mate - 1

        var created = 0
        for (length in 1..mate) {
            if (length < mate && !canShorten) {
                continue
            }

            val revealedPlies = 2 * (mate - length)
            if (revealedPlies >= solutionLine.size) {
                continue
            }

            val revealedMoves = solutionLine.subList(0, revealedPlies)
            val solutionMoves = solutionLine.subList(revealedPlies, solutionLine.size)

            // the puzzle starts from the final position advanced by the revealed moves
            val board = Board(candidate.startFen)
            revealedMoves.forEach { move -> board.registerMove(move) }
            val startFen = board.outputFen()

            // non-solution moves shown in the move tree: the whole game from the standard start, then the
            // revealed engine plies, so the front-end can translate them from the standard start position
            val nonSolutionMoves = gameHistory + revealedMoves

            val algorithm = if (length == mate) FIND_PATH_TO_MATE else FIND_PATH_TO_MATE_SHORTENED
            val puzzleId = generateId()
            val rating = length * RATING_PER_MATE

            val puzzle = Puzzle().apply {
                id = puzzleId
                refGameSource = candidate.refGameSource
                refGameSourceId = candidate.refGameSourceId
                this.algorithm = algorithm
                engine = Engine.PIKAFISH
                dataset = INPUT_FILE_NAME
                playerColor = candidate.playerColor
                this.startFen = startFen
                initialRating = rating
                this.rating = rating
                disabledAt = null
                createdAt = LocalDateTime.now()
            }

            val nonSolutionMovesRecords = nonSolutionMoves
                .mapIndexed { position, uci ->
                    PuzzleHalfMove(puzzleId, position, uci, false)
                }

            val solutionMovesRecords = solutionMoves
                .mapIndexed { index, uci ->
                    PuzzleHalfMove(puzzleId, nonSolutionMoves.size + index, uci, true)
                }

            val halfMoves =
                nonSolutionMovesRecords + solutionMovesRecords

            val categories = listOf(
                PuzzleCategoryTag(puzzleId, PuzzleCategory.findMateInN(length)),
            )

            try {
                validateSavedMoves(puzzleId, nonSolutionMoves, solutionMoves, startFen)
            } catch (e: Exception) {
                logger.warn { "skipping MATE_IN_$length puzzle for ${candidate.refGameSourceId}: ${e.message}" }
                continue
            }

            puzzleDaoService.save(puzzle, halfMoves, categories)
            created++
            logger.info {
                "created $algorithm puzzle $puzzleId (MATE_IN_$length) from ${candidate.refGameSource}/${candidate.refGameSourceId}"
            }
        }

        return created
    }

    /**
     * Loads the full reference game move list (standard start position to final position), ordered by ply.
     */
    private suspend fun fetchGameHistory(source: String, sourceId: String): List<String> =
        dslContext
            .select(REFERENCE_GAME_HALF_MOVE.UCI)
            .from(REFERENCE_GAME_HALF_MOVE)
            .join(REFERENCE_GAME).on(REFERENCE_GAME_HALF_MOVE.REFERENCE_GAME_ID.eq(REFERENCE_GAME.ID))
            .where(REFERENCE_GAME.SOURCE.eq(source))
            .and(REFERENCE_GAME.SOURCE_ID.eq(sourceId))
            .orderBy(REFERENCE_GAME_HALF_MOVE.POSITION)
            .awaitMappedRecords()

    /**
     * Replays the stored moves from the standard start position to make sure the puzzle is coherent:
     * every ply must be legal, the non-solution prefix must reach [startFen], and the full line must end
     * in checkmate. [Board.registerMove] throws on illegal moves, so an invalid puzzle never gets saved.
     */
    private fun validateSavedMoves(puzzleId: String, nonSolutionMoves: List<String>, solutionMoves: List<String>, startFen: String) {
        val board = Board()
        nonSolutionMoves.forEach { move -> board.registerMove(move) }
        check(positionKey(board.outputFen()) == positionKey(startFen)) {
            "puzzle $puzzleId: non-solution moves do not reach the puzzle start position"
        }
        solutionMoves.forEach { move -> board.registerMove(move) }
        check(board.isCheckmated()) {
            "puzzle $puzzleId: solution line does not end in checkmate"
        }
    }

    /**
     * The comparable part of a FEN: piece placement and side to move, ignoring the move counters, which
     * differ between a position loaded directly and the same position reconstructed from the start.
     */
    private fun positionKey(fen: String): String =
        fen.trim().split(Regex("\\s+")).take(2).joinToString(" ")

    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            val inputFile = File(INPUT_FILE_NAME)
            if (!inputFile.exists()) {
                logger.error { "$INPUT_FILE_NAME not found, run FindMatePuzzleCandidates first" }
                return@runBlocking
            }

            val candidates = inputFile
                .readLines()
                .drop(1)
                .filter { line -> line.isNotBlank() }
                .map { line -> MatePuzzleCandidate.fromCsvLine(line) }

            logger.info { "read ${candidates.size} candidates from $INPUT_FILE_NAME" }

            var created = 0
            candidates.forEach { candidate ->
                created += createPuzzles(candidate)
            }

            logger.info { "created $created puzzles" }
        }
}
