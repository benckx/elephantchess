package io.elephantchess.scripts.puzzles

import io.elephantchess.db.dao.codegen.Tables.PUZZLE
import io.elephantchess.db.dao.codegen.tables.pojos.Puzzle
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleCategoryTag
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleHalfMove
import io.elephantchess.db.services.PuzzleDaoService
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.generateId
import io.elephantchess.model.Engine
import io.elephantchess.model.PuzzleAlgo
import io.elephantchess.model.PuzzleCategory
import io.elephantchess.scripts.KoinScriptInit
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Creates MATE_IN_1, MATE_IN_2, ... puzzles from the candidates produced by [FindMatePuzzleCandidates].
 *
 * Each candidate becomes a puzzle starting from the final game position, where the solution is the
 * engine line leading to the forced mate. Candidates whose reference game already has a puzzle are
 * skipped, so the script is safe to re-run.
 */
object CreatePuzzlesFromCsv : KoinScriptInit() {

    private const val INPUT_FILE_NAME = "mate_puzzle_candidates.csv"
    private const val PUZZLE_ID_SIZE = 8

    // mirrors the existing puzzles: MATE_IN_1 -> 400, MATE_IN_2 -> 800, ...
    private const val RATING_PER_MATE = 400

    private val dslContext by inject<DSLContext>()
    private val puzzleDaoService by inject<PuzzleDaoService>()

    private suspend fun fetchUsedReferenceGameSourceIds(): Set<String> =
        dslContext
            .select(PUZZLE.REF_GAME_SOURCE_ID)
            .from(PUZZLE)
            .awaitMappedRecords<String>()
            .toSet()

    private suspend fun createPuzzle(candidate: MatePuzzleCandidate) {
        val puzzleId = generateId(PUZZLE_ID_SIZE)
        val rating = candidate.mate * RATING_PER_MATE

        val puzzle = Puzzle(
            puzzleId,
            candidate.refGameSource,
            candidate.refGameSourceId,
            PuzzleAlgo.FIND_PATH_TO_MATE,
            Engine.PIKAFISH,
            null,
            candidate.playerColor,
            candidate.startFen,
            rating,
            rating,
            null
        )

        val halfMoves = candidate.solutionMoves.mapIndexed { position, uci ->
            PuzzleHalfMove(puzzleId, position, uci, true)
        }

        val categories = listOf(
            PuzzleCategoryTag(puzzleId, PuzzleCategory.findMateInN(candidate.mate))
        )

        puzzleDaoService.save(puzzle, halfMoves, categories)
        logger.info { "created puzzle $puzzleId (MATE_IN_${candidate.mate}) from ${candidate.refGameSource}/${candidate.refGameSourceId}" }
    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val inputFile = File(INPUT_FILE_NAME)
        if (!inputFile.exists()) {
            logger.error { "$INPUT_FILE_NAME not found, run FindMatePuzzleCandidates first" }
            return@runBlocking
        }

        val usedSourceIds = fetchUsedReferenceGameSourceIds()
        val candidates = inputFile
            .readLines()
            .drop(1)
            .filter { line -> line.isNotBlank() }
            .map { line -> MatePuzzleCandidate.fromCsvLine(line) }

        logger.info { "read ${candidates.size} candidates from $INPUT_FILE_NAME" }

        var created = 0
        candidates.forEach { candidate ->
            if (candidate.refGameSourceId in usedSourceIds) {
                logger.info { "skipping ${candidate.refGameSourceId}: already has a puzzle" }
            } else {
                createPuzzle(candidate)
                created++
            }
        }

        logger.info { "created $created puzzles" }
    }

}
