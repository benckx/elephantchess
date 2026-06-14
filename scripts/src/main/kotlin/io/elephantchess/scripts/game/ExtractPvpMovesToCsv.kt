package io.elephantchess.scripts.game

import com.opencsv.CSVWriter
import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_MOVE
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.generateId
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType.PVP
import io.elephantchess.model.TimeControlMode
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.servicelayer.services.GameDataService
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils.insecure
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

private const val MIN_MOVE_INDEX = 6
private const val DEFAULT_OUTPUT_PATH = "pvp_game_moves.csv"
private const val GAMES_PER_FILE = 1000
private const val ANONYMIZE = true

private val CSV_HEADER = arrayOf(
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
    "analysis",
)

object ExtractPvpMovesToCsv : KoinScriptInit() {

    private val dslContext by inject<DSLContext>()
    private val gameDataService by inject<GameDataService>()

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val outputPath = args.firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_OUTPUT_PATH
        exportCsv(outputPath, ANONYMIZE)
    }

    private suspend fun exportCsv(outputPath: String, anonymize: Boolean) {
        val inviterUser = USER.`as`("inviter_user")
        val inviteeUser = USER.`as`("invitee_user")

        val rows = dslContext
            .select(
                GAME.ID,
                GAME.CREATED,
                GAME.GAME_STATUS,
                GAME.INVITER_COLOR,
                GAME.IS_RATED,
                GAME.TIME_CONTROL_MODE,
                GAME.TIME_CONTROL_BASE,
                GAME.TIME_CONTROL_INCREMENT,
                GAME.TIME_CONTROL_CATEGORY,
                GAME.OUTCOME,
                GAME.JOIN_SOURCE,
                GAME.INVITER_RATING_FROM,
                GAME.INVITER_RATING_TO,
                GAME.INVITEE_RATING_FROM,
                GAME.INVITEE_RATING_TO,
                GAME_MOVE.POSITION,
                GAME_MOVE.UCI,
                GAME_MOVE.EVENT_TIME,
                GAME_MOVE.POSITION,
                inviterUser.ID,
                inviterUser.HANDLE,
                inviteeUser.ID,
                inviteeUser.HANDLE
            )
            .from(GAME)
            .join(GAME_MOVE).on(GAME_MOVE.GAME_ID.eq(GAME.ID))
            .leftJoin(inviterUser).on(inviterUser.ID.eq(GAME.INVITER))
            .leftJoin(inviteeUser).on(inviteeUser.ID.eq(GAME.INVITEE))
            .where(GAME.CURRENT_HALF_MOVE_INDEX.ge(MIN_MOVE_INDEX))
            .and(GAME.VARIANT.eq(Variant.XIANGQI))
            .orderBy(GAME.CREATED.asc(), GAME.ID.asc(), GAME_MOVE.POSITION.asc())
            .awaitRecords()

        // Replay each game's moves to compute the FEN key (FEN without the full-move counter) for every move.
        // The key matches the position resulting from the played move, which is how engine analysis is keyed.
        var currentGameId: String? = null
        var board = Board(DEFAULT_START_FEN)
        val fenKeys = rows.map { row ->
            val gameId = row.get(GAME.ID)
            if (gameId != currentGameId) {
                currentGameId = gameId
                board = Board(DEFAULT_START_FEN)
            }
            board.registerMove(row.get(GAME_MOVE.UCI))
            resetFullMoveCount(board.outputFen())
        }

        val analysisByGameAndFenKey = rows
            .map { row -> row.get(GAME.ID) }
            .distinct()
            .associateWith { gameId ->
                gameDataService
                    .fetchAnalysisData(GameId(PVP, gameId))
                    .entries
                    .filter { entry -> entry.line != null }
                    .associate { entry -> entry.fen to entry.line!! }
            }

        // Assign each game (in encounter order) to a 1000-game batch so we can write one CSV per batch.
        val batchByGameId = rows
            .map { row -> row.get(GAME.ID) }
            .distinct()
            .withIndex()
            .associate { (gameIndex, gameId) -> gameId to gameIndex / GAMES_PER_FILE }

        val rowsByBatch = rows.indices.groupBy { index -> batchByGameId.getValue(rows[index].get(GAME.ID)) }

        val anonymizedNames: Map<String, String> = if (anonymize) {
            rows
                .flatMap { row ->
                    listOf(
                        row.get(inviterUser.HANDLE) ?: guestName(row.get(inviterUser.ID)),
                        row.get(inviteeUser.HANDLE) ?: guestName(row.get(inviteeUser.ID))
                    )
                }
                .distinct()
                .associateWith { insecure().nextAlphanumeric(12) }
        } else {
            emptyMap()
        }

        var totalRows = 0
        rowsByBatch.toSortedMap().forEach { (batch, rowIndices) ->
            val batchOutputPath = batchOutputPath(outputPath, batch)
            File(batchOutputPath).bufferedWriter().use { bufferedWriter ->
                CSVWriter(bufferedWriter).use { writer ->
                    writer.writeNext(CSV_HEADER)

                    rowIndices.forEach { index ->
                        val row = rows[index]
                        val gameId = if (anonymize) "n/a" else row.get(GAME.ID).toString()
                        val inviterColor = row.get(GAME.INVITER_COLOR)
                        val inviterHandle = row.get(inviterUser.HANDLE) ?: guestName(row.get(inviterUser.ID))
                        val inviteeHandle = row.get(inviteeUser.HANDLE) ?: guestName(row.get(inviteeUser.ID))
                        val inviterIsRed = inviterColor == Color.RED
                        val redHandle = if (inviterIsRed) inviterHandle else inviteeHandle
                        val blackHandle = if (inviterIsRed) inviteeHandle else inviterHandle
                        val redPlayerName = anonymizedNames[redHandle] ?: redHandle
                        val blackPlayerName = anonymizedNames[blackHandle] ?: blackHandle
                        val redPlayerRating = if (inviterIsRed) {
                            PlayerRating(row.get(GAME.INVITER_RATING_FROM), row.get(GAME.INVITER_RATING_TO))
                        } else {
                            PlayerRating(row.get(GAME.INVITEE_RATING_FROM), row.get(GAME.INVITEE_RATING_TO))
                        }
                        val blackPlayerRating = if (inviterIsRed) {
                            PlayerRating(row.get(GAME.INVITEE_RATING_FROM), row.get(GAME.INVITEE_RATING_TO))
                        } else {
                            PlayerRating(row.get(GAME.INVITER_RATING_FROM), row.get(GAME.INVITER_RATING_TO))
                        }

                        val fenKey = fenKeys[index]
                        val analysis = analysisByGameAndFenKey[row.get(GAME.ID)]?.get(fenKey) ?: ""

                        writer.writeNext(
                            arrayOf(
                                row.get(GAME_MOVE.EVENT_TIME).toString(),
                                row.get(GAME_MOVE.POSITION).toString(),
                                row.get(GAME_MOVE.UCI),
                                gameId,
                                redPlayerName,
                                blackPlayerName,
                                redPlayerRating.before?.toString() ?: "",
                                redPlayerRating.after?.toString() ?: "",
                                blackPlayerRating.before?.toString() ?: "",
                                blackPlayerRating.after?.toString() ?: "",
                                timeControl(
                                    row.get(GAME.TIME_CONTROL_MODE),
                                    row.get(GAME.TIME_CONTROL_BASE),
                                    row.get(GAME.TIME_CONTROL_INCREMENT),
                                ),
                                row.get(GAME.TIME_CONTROL_CATEGORY)?.name ?: "",
                                if (row.get(GAME.IS_RATED) == true) "rated" else "casual",
                                row.get(GAME.GAME_STATUS)?.name ?: "",
                                row.get(GAME.OUTCOME)?.name ?: "",
                                row.get(GAME.JOIN_SOURCE)?.name ?: "",
                                analysis,
                            )
                        )
                    }
                }
            }
            totalRows += rowIndices.size
            println("wrote ${rowIndices.size} rows to $batchOutputPath")
        }

        println("wrote $totalRows rows across ${rowsByBatch.size} file(s)")

        val batchPaths = rowsByBatch.keys.sorted().map { batch -> batchOutputPath(outputPath, batch) }
        val zipPath = zipOutputPath(outputPath)
        ZipOutputStream(File(zipPath).outputStream().buffered()).use { zip ->
            batchPaths.forEach { batchPath ->
                val batchFile = File(batchPath)
                zip.putNextEntry(ZipEntry(batchFile.name))
                batchFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        println("zipped ${batchPaths.size} file(s) to $zipPath")

        exitProcess(0)
    }

    private fun zipOutputPath(outputPath: String): String {
        val file = File(outputPath)
        val name = file.name
        val month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val dotIndex = name.lastIndexOf('.')
        val baseName = if (dotIndex >= 0) name.substring(0, dotIndex) else name
        val newName = "${baseName}_${month}.zip"
        return file.parentFile?.let { File(it, newName).path } ?: newName
    }

    private fun batchOutputPath(outputPath: String, batch: Int): String {
        val file = File(outputPath)
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        val suffix = "_%03d".format(batch + 1)
        val newName = if (dotIndex >= 0) {
            name.substring(0, dotIndex) + suffix + name.substring(dotIndex)
        } else {
            name + suffix
        }
        return file.parentFile?.let { File(it, newName).path } ?: newName
    }

    private fun guestName(userId: String): String = "guest #$userId"

    private fun timeControl(mode: TimeControlMode?, base: Int?, increment: Int?): String =
        when (mode) {
            TimeControlMode.GAME_TIME -> "${base ?: 0}+${increment ?: 0}"
            TimeControlMode.MOVE_TIME -> "${base ?: 0}/move"
            null -> ""
        }

    private data class PlayerRating(val before: Int?, val after: Int?)

}
