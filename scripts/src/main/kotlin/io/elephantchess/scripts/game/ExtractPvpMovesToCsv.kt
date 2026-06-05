package io.elephantchess.scripts.game

import com.opencsv.CSVWriter
import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_MOVE
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.io.File

private const val MIN_MOVE_INDEX = 6
private const val DEFAULT_OUTPUT_PATH = "pvp_game_moves.csv"

object ExtractPvpMovesToCsv : KoinScriptInit() {

    private val dslContext by inject<DSLContext>()

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val outputPath = args.firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_OUTPUT_PATH
        exportCsv(outputPath)
    }

    private suspend fun exportCsv(outputPath: String) {
        val inviterUser = USER.`as`("inviter_user")
        val inviteeUser = USER.`as`("invitee_user")

        val rows = dslContext
            .select(
                GAME.ID,
                GAME.CREATED,
                GAME.GAME_STATUS,
                GAME.INVITER_COLOR,
                GAME.INVITER_RATING_FROM,
                GAME.INVITER_RATING_TO,
                GAME.INVITEE_RATING_FROM,
                GAME.INVITEE_RATING_TO,
                GAME_MOVE.POSITION,
                GAME_MOVE.UCI,
                GAME_MOVE.EVENT_TIME,
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

        File(outputPath).bufferedWriter().use { bufferedWriter ->
            CSVWriter(bufferedWriter).use { writer ->
                writer.writeNext(
                    arrayOf(
                        "timestamp",
                        "move",
                        "gameId",
                        "redPlayerName",
                        "blackPlayerName",
                        "eloBefore",
                        "eloAfter",
                        "gameStatusAtEnd",
                    )
                )

                rows.forEach { row ->
                    val inviterColor = row.get(GAME.INVITER_COLOR)
                    val inviterHandle = row.get(inviterUser.HANDLE) ?: guestName(row.get(inviterUser.ID))
                    val inviteeHandle = row.get(inviteeUser.HANDLE) ?: guestName(row.get(inviteeUser.ID))
                    val inviterIsRed = inviterColor == Color.RED
                    val redPlayerName = if (inviterIsRed) inviterHandle else inviteeHandle
                    val blackPlayerName = if (inviterIsRed) inviteeHandle else inviterHandle
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
                    val position = row.get(GAME_MOVE.POSITION)
                    val isRedMove = position % 2 == 0
                    val playerRating = if (isRedMove) redPlayerRating else blackPlayerRating

                    writer.writeNext(
                        arrayOf(
                            row.get(GAME_MOVE.EVENT_TIME).toString(),
                            row.get(GAME_MOVE.UCI),
                            row.get(GAME.ID).toString(),
                            redPlayerName,
                            blackPlayerName,
                            playerRating.before?.toString() ?: "",
                            playerRating.after?.toString() ?: "",
                            row.get(GAME.GAME_STATUS)?.name ?: "",
                        )
                    )
                }
            }
        }

        println("wrote ${rows.size} rows to $outputPath")
    }

    private fun guestName(userId: String): String = "guest #$userId"

    private data class PlayerRating(val before: Int?, val after: Int?)
}
