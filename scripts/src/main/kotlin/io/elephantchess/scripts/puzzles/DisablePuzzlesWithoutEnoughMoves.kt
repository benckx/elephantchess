package io.elephantchess.scripts.puzzles

import io.elephantchess.db.dao.codegen.Tables.PUZZLE
import io.elephantchess.db.dao.codegen.Tables.PUZZLE_HALF_MOVE
import io.elephantchess.db.dao.codegen.tables.pojos.Puzzle
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleHalfMove
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.fixed
import io.elephantchess.scripts.KoinScriptInit
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.koin.core.component.inject

private val logger = KotlinLogging.logger {}

/**
 * Goes over every puzzle and replays its recorded solution, checking that the player always has at
 * least [PuzzleSolvabilityValidator.MIN_LEGAL_MOVES_PER_STEP] legal moves available at each of their
 * turns. Puzzles failing this check (e.g. puzzles that start in check and can be dragged into a
 * perpetual-check loop) are flagged as `enabled = false` so they are no longer served when picking
 * the next puzzle to assign.
 */
object DisablePuzzlesWithoutEnoughMoves : KoinScriptInit() {

    private val dslContext by inject<DSLContext>()

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val puzzles = dslContext
            .selectFrom(PUZZLE)
            .awaitMappedRecords<Puzzle>()

        val movesByPuzzle = dslContext
            .selectFrom(PUZZLE_HALF_MOVE)
            .orderBy(PUZZLE_HALF_MOVE.PUZZLE_ID, PUZZLE_HALF_MOVE.POSITION)
            .awaitMappedRecords<PuzzleHalfMove>()
            .groupBy { halfMove -> halfMove.puzzleId }

        logger.info { "checking ${puzzles.size} puzzles" }

        val puzzleIdsToDisable = puzzles.mapNotNull { puzzle ->
            val moves = movesByPuzzle[puzzle.id]?.sortedBy { it.position } ?: emptyList()
            val setupMoves = moves.filterNot { halfMove -> halfMove.isSolution }.map { halfMove -> halfMove.uci }
            val solutionMoves = moves.filter { halfMove -> halfMove.isSolution }.map { halfMove -> halfMove.uci }

            val isValid = try {
                PuzzleSolvabilityValidator.hasEnoughMovesAtEachPlayerStep(
                    setupMoves = setupMoves,
                    solutionMoves = solutionMoves,
                )
            } catch (e: Exception) {
                logger.warn { "could not validate puzzle ${puzzle.id} due to ${e::class.simpleName}: ${e.message}" }
                false
            }

            if (isValid) null else puzzle.id
        }

        logger.info { "disabling ${puzzleIdsToDisable.size} puzzles: $puzzleIdsToDisable" }

        if (puzzleIdsToDisable.isNotEmpty()) {
            dslContext.transactionCoroutine { cfg ->
                DSL
                    .using(cfg)
                    .update(PUZZLE.fixed())
                    .set(PUZZLE.ENABLED.fixed(), false)
                    .where(PUZZLE.ID.`in`(puzzleIdsToDisable))
                    .awaitExecute()
            }
        }
    }
}
