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

}
