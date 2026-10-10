package io.elephantchess.scripts.puzzles

import io.elephantchess.db.dao.codegen.tables.pojos.Puzzle
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleCategoryTag
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleHalfMove
import io.elephantchess.db.services.PuzzleDaoService
import io.elephantchess.db.utils.generateId
import io.elephantchess.model.Engine
import io.elephantchess.model.PuzzleAlgo
import io.elephantchess.model.PuzzleCategory
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.xiangqi.Board
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
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
    private const val PUZZLE_ID_SIZE = 8

    // mirrors the existing puzzles: MATE_IN_1 -> 400, MATE_IN_2 -> 800, ...
    private const val RATING_PER_MATE = 400

    private val puzzleDaoService by inject<PuzzleDaoService>()

    /**
     * Creates the full-length puzzle plus every shorter shortened variant for a candidate.
     * @return the number of puzzles created.
     */
    private suspend fun createPuzzles(candidate: MatePuzzleCandidate): Int {
        val solutionLine = candidate.solutionMoves
        val mate = candidate.mate

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

            val algorithm =
                if (length == mate) PuzzleAlgo.FIND_PATH_TO_MATE else PuzzleAlgo.FIND_PATH_TO_MATE_SHORTENED
            val puzzleId = generateId(PUZZLE_ID_SIZE)
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

            val halfMoves =
                revealedMoves.mapIndexed { position, uci -> PuzzleHalfMove(puzzleId, position, uci, false) } +
                        solutionMoves.mapIndexed { index, uci ->
                            PuzzleHalfMove(puzzleId, revealedPlies + index, uci, true)
                        }

            val categories = listOf(
                PuzzleCategoryTag(puzzleId, PuzzleCategory.findMateInN(length))
            )

            puzzleDaoService.save(puzzle, halfMoves, categories)
            created++
            logger.info { "created $algorithm puzzle $puzzleId (MATE_IN_$length) from ${candidate.refGameSource}/${candidate.refGameSourceId}" }
        }

        return created
    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
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
