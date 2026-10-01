package io.elephantchess.csvdump

import io.elephantchess.engines.protocol.model.InfoLineResult
import io.elephantchess.xiangqi.HalfMove
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import kotlin.time.Instant

/**
 * A single half move (ply) of a PvP game, as stored in one row of a `pvp_game_moves_*.csv` file
 * produced by the `ExtractPvpMovesToCsv` script. Only move-specific data lives here; game-level
 * metadata (players, ratings, outcome, ...) is held once per game by [PvpGame].
 */
data class PvpMove(
    val timestamp: Instant,
    val moveIndex: Int,
    val move: HalfMove,
    val moveAnalysis: InfoLineResult?,
    val engineAnalysis: InfoLineResult?,
    val cpl: Int?,
) {

    val moveUci: String get() = move.toUci()

    companion object {

        fun fromCsvRow(row: CsvRow): PvpMove =
            PvpMove(
                timestamp = Instant.parse(row.value("timestamp").trim()),
                moveIndex = row.value("move_index").trim().toInt(),
                move = parseMoveFromUci(row.value("move").trim()),
                moveAnalysis = row.infoLineOrNull("move_analysis"),
                engineAnalysis = row.infoLineOrNull("engine_analysis"),
                cpl = row.intOrNull("cpl"),
            )
    }
}
