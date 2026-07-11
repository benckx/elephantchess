package io.elephantchess.csvdump

import io.elephantchess.engines.protocol.model.InfoLineResult

/**
 * A single parsed CSV row keyed by column name, with typed accessors used while building the
 * [PvpMove] and [PvpGame] domain objects.
 */
class CsvRow(private val columns: Map<String, String>) {

    fun value(column: String): String =
        columns[column] ?: throw IllegalArgumentException("missing column '$column' in CSV row")

    fun intOrNull(column: String): Int? = value(column).trim().ifBlank { null }?.toInt()

    fun infoLineOrNull(column: String): InfoLineResult? =
        value(column).trim().ifBlank { null }?.let { InfoLineResult.parseInfoLine(it) }

    companion object {

        /**
         * Column headers, in the order written by `ExtractPvpMovesToCsv`.
         */
        val HEADERS: List<String> = listOf(
            "timestamp",
            "move_index",
            "move",
            "game_id",
            "red_player",
            "black_player",
            "red_elo_before",
            "red_elo_after",
            "black_elo_before",
            "black_elo_after",
            "time_control",
            "time_control_category",
            "rating_mode",
            "game_status",
            "outcome",
            "game_join_source",
            "move_analysis",
            "engine_analysis",
            "cpl",
            "red_account_age",
            "red_user_type",
            "black_account_age",
            "black_user_type",
        )
    }
}
