package io.elephantchess.scripts.puzzles

import io.elephantchess.xiangqi.Board

/**
 * Validates that a puzzle remains "solvable" in a meaningful way: while replaying the recorded
 * solution, the player must have several legal moves available at every one of their turns.
 *
 * Puzzles where the player is too constrained (for example a puzzle that starts in check and where
 * the only legal moves keep the player in a perpetual-check loop) are flagged as invalid so they can
 * be disabled and no longer served when picking the next puzzle to assign.
 */
object PuzzleSolvabilityValidator {

    /**
     * Replays the [setupMoves] from [startFen] and then walks through the [solutionMoves], checking
     * that, at every player turn, at least [minLegalMoves] legal moves are available.
     *
     * The player is the side to move once the setup moves have been applied (i.e. the side playing
     * the first solution move).
     *
     * @return `true` if every player step offers at least [minLegalMoves] legal moves, `false` otherwise.
     */
    fun hasEnoughMovesAtEachPlayerStep(
        setupMoves: List<String>,
        solutionMoves: List<String>,
        minLegalMoves: Int,
    ): Boolean {
        val board = Board()
        setupMoves.forEach { move -> board.registerMove(move) }

        val playerColor = board.colorToPlay()

        solutionMoves.forEach { move ->
            if (board.colorToPlay() == playerColor) {
                val legalMovesCount = board.listLegalMoves(playerColor).size
                if (legalMovesCount < minLegalMoves) {
                    return false
                }
            }
            board.registerMove(move)
        }

        return true
    }

}
