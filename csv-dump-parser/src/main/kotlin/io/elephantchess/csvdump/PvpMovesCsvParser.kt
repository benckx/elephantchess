package io.elephantchess.csvdump

import com.opencsv.CSVReaderHeaderAware
import java.io.File
import java.io.Reader
import java.util.zip.ZipFile

/**
 * Parses `pvp_game_moves_*.csv` dumps produced by the `ExtractPvpMovesToCsv` script into
 * raw [CsvRow]s or aggregated [PvpGame]s. Whole dumps zipped by the script can be read directly
 * with the `*FromZip` helpers, which concatenate every `.csv` entry inside the archive.
 */
object PvpMovesCsvParser {

    fun parseRows(file: File): List<CsvRow> =
        file.bufferedReader().use { reader -> parseRows(reader) }

    fun parseRows(fileName: String): List<CsvRow> = parseRows(File(fileName))

    fun parseRows(reader: Reader): List<CsvRow> {
        val csvReader = CSVReaderHeaderAware(reader)
        val rows = mutableListOf<CsvRow>()
        while (true) {
            val row = csvReader.readMap() ?: break
            rows += CsvRow(row)
        }
        return rows
    }

    fun parseGames(file: File): List<PvpGame> = PvpGame.fromCsvRows(parseRows(file))

    fun parseGames(fileName: String): List<PvpGame> = parseGames(File(fileName))

    fun parseGames(reader: Reader): List<PvpGame> = PvpGame.fromCsvRows(parseRows(reader))

    /**
     * Reads every `.csv` entry of the given ZIP archive (in name order) and returns all of their
     * rows combined.
     */
    fun parseRowsFromZip(file: File): List<CsvRow> {
        val rows = mutableListOf<CsvRow>()
        ZipFile(file).use { zip ->
            zip.entries()
                .asSequence()
                .filter { entry -> !entry.isDirectory && entry.name.endsWith(".csv", ignoreCase = true) }
                .sortedBy { entry -> entry.name }
                .forEach { entry ->
                    zip.getInputStream(entry).bufferedReader().use { reader -> rows += parseRows(reader) }
                }
        }
        return rows
    }

    fun parseRowsFromZip(fileName: String): List<CsvRow> = parseRowsFromZip(File(fileName))

    fun parseGamesFromZip(file: File): List<PvpGame> = PvpGame.fromCsvRows(parseRowsFromZip(file))

    fun parseGamesFromZip(fileName: String): List<PvpGame> = parseGamesFromZip(File(fileName))
}
