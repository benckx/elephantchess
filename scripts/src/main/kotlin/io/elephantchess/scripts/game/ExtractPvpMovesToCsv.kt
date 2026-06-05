package io.elephantchess.scripts.game

import com.opencsv.CSVWriter
import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_MOVE
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.model.GameEventType
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.xiangqi.Color
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
                inviterUser.HANDLE,
                inviteeUser.HANDLE
            )
            .from(GAME)
            .join(GAME_MOVE).on(GAME_MOVE.GAME_ID.eq(GAME.ID))
            .leftJoin(inviterUser).on(inviterUser.ID.eq(GAME.INVITER))
            .leftJoin(inviteeUser).on(inviteeUser.ID.eq(GAME.INVITEE))
            .where(GAME.CURRENT_HALF_MOVE_INDEX.ge(MIN_MOVE_INDEX))
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
                    val inviterHandle = row.get(inviterUser.HANDLE)
                    val inviteeHandle = row.get(inviteeUser.HANDLE)
                    val redPlayerName = if (inviterColor == Color.RED) inviterHandle else inviteeHandle
                    val blackPlayerName = if (inviterColor == Color.RED) inviteeHandle else inviterHandle
                    val redEloBefore = if (inviterColor == Color.RED) row.get(GAME.INVITER_RATING_FROM) else row.get(GAME.INVITEE_RATING_FROM)
                    val blackEloBefore = if (inviterColor == Color.RED) row.get(GAME.INVITEE_RATING_FROM) else row.get(GAME.INVITER_RATING_FROM)
                    val redEloAfter = if (inviterColor == Color.RED) row.get(GAME.INVITER_RATING_TO) else row.get(GAME.INVITEE_RATING_TO)
                    val blackEloAfter = if (inviterColor == Color.RED) row.get(GAME.INVITEE_RATING_TO) else row.get(GAME.INVITER_RATING_TO)
                    val position = row.get(GAME_MOVE.POSITION)
                    val isRedMove = position % 2 == 0

                    writer.writeNext(
                        arrayOf(
                            row.get(GAME_MOVE.EVENT_TIME).toString(),
                            row.get(GAME_MOVE.UCI),
                            row.get(GAME.ID),
                            redPlayerName ?: "",
                            blackPlayerName ?: "",
                            (if (isRedMove) redEloBefore else blackEloBefore)?.toString() ?: "",
                            (if (isRedMove) redEloAfter else blackEloAfter)?.toString() ?: "",
                            row.get(GAME.GAME_STATUS)?.name ?: GameEventType.CREATED.name,
                        )
                    )
                }
            }
        }

        println("wrote ${rows.size} rows to $outputPath")
    }
}
