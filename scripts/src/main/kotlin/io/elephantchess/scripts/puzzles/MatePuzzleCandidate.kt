package io.elephantchess.scripts.puzzles

import io.elephantchess.xiangqi.Color

/**
 * A "mate in [mate]" candidate found by [FindMatePuzzleCandidates] and consumed by [CreatePuzzlesFromCsv].
 *
 * It is the position reached at the end of a reference game ([startFen]) from which the side to move
 * ([playerColor]) has a forced checkmate. [solutionMoves] is the engine principal variation leading to
 * that mate (both players' moves), so [mate] is the number of moves the player has to find.
 *
 * Serialized as a single CSV line (`;` separated, moves joined by `,`). A FEN never contains `;` or `,`.
 */
data class MatePuzzleCandidate(
    val refGameSource: String,
    val refGameSourceId: String,
    val playerColor: Color,
    val mate: Int,
    val depth: Int,
    val startFen: String,
    val solutionMoves: List<String>,
) {

    fun toCsvLine(): String =
        listOf(
            refGameSource,
            refGameSourceId,
            playerColor.name,
            mate.toString(),
            depth.toString(),
            startFen,
            solutionMoves.joinToString(",")
        ).joinToString(SEPARATOR)

    companion object {

        private const val SEPARATOR = ";"

        const val CSV_HEADER = "ref_game_source;ref_game_source_id;player_color;mate;depth;start_fen;solution_moves"

        fun fromCsvLine(line: String): MatePuzzleCandidate {
            val columns = line.split(SEPARATOR)
            return MatePuzzleCandidate(
                refGameSource = columns[0],
                refGameSourceId = columns[1],
                playerColor = Color.valueOf(columns[2]),
                mate = columns[3].toInt(),
                depth = columns[4].toInt(),
                startFen = columns[5],
                solutionMoves = columns[6].split(",")
            )
        }

    }

}
