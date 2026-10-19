package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME_SEARCH_QUERY
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_PLAYER
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.model.AnalysisStatus
import io.elephantchess.model.Outcome
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.component.inject
import java.time.LocalDate
import kotlin.test.assertEquals

class DatabaseServiceTest : ServiceTest() {

    private val databaseService by inject<DatabaseService>()
    private val dslContext by inject<DSLContext>()

    @AfterEach
    fun afterEach() = runTest {
        listOf(
            REFERENCE_GAME_SEARCH_QUERY,
            REFERENCE_GAME,
            REFERENCE_PLAYER
        ).forEach { table ->
            dslContext
                .deleteFrom(table)
                .awaitExecute()
        }
    }

    @Test
    fun `search formats player names as canonical plus chinese name in brackets`() = runTest {
        dslContext.insertInto(REFERENCE_PLAYER)
            .set(REFERENCE_PLAYER.ID, "p_red")
            .set(REFERENCE_PLAYER.SOURCE_NAME, "Hu Ronghua")
            .set(REFERENCE_PLAYER.CANONICAL_NAME, "Hu Ronghua")
            .set(REFERENCE_PLAYER.CHINESE_NAME, "胡荣华")
            .set(REFERENCE_PLAYER.IS_VISIBLE, true)
            .awaitExecute()

        dslContext.insertInto(REFERENCE_PLAYER)
            .set(REFERENCE_PLAYER.ID, "p_blk")
            .set(REFERENCE_PLAYER.SOURCE_NAME, "Liu Dahua")
            .set(REFERENCE_PLAYER.CANONICAL_NAME, "Liu Dahua")
            .set(REFERENCE_PLAYER.CHINESE_NAME, "柳大华")
            .set(REFERENCE_PLAYER.IS_VISIBLE, true)
            .awaitExecute()

        dslContext.insertInto(REFERENCE_GAME)
            .set(REFERENCE_GAME.ID, "g0000001")
            .set(REFERENCE_GAME.NUMBER_OF_HALF_MOVES, 0)
            .set(REFERENCE_GAME.DATE, LocalDate.of(2026, 1, 1))
            .set(REFERENCE_GAME.YEAR, 2026)
            .set(REFERENCE_GAME.RED_PLAYER, "p_red")
            .set(REFERENCE_GAME.BLACK_PLAYER, "p_blk")
            .set(REFERENCE_GAME.OUTCOME, Outcome.DRAW)
            .set(
                REFERENCE_GAME.FINAL_FEN,
                "rheakaehr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RHEAKAEHR w - - 0 1"
            )
            .set(REFERENCE_GAME.IS_CHECKMATE, false)
            .set(REFERENCE_GAME.IS_STALEMATE, false)
            .set(REFERENCE_GAME.ANALYSIS_STATUS, AnalysisStatus.CANCELLED)
            .awaitExecute()

        val result = databaseService.search(
            dateStart = null,
            dateEnd = null,
            playerName = null,
            playerIds = emptyList(),
            playerColor = null,
            eventName = null,
            eventIds = emptyList(),
            fen = null,
            offset = null,
            userId = "test-user",
        )

        assertEquals("Hu Ronghua (胡荣华)", result.entries.single().redPlayerName)
        assertEquals("Liu Dahua (柳大华)", result.entries.single().blackPlayerName)
    }
}
