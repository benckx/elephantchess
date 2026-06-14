package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_PLAYER
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.servicelayer.dto.analysis.OpeningReferencePlayerNextMovesRequest
import io.elephantchess.xiangqi.Color
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.component.inject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpeningServiceTest : ServiceTest() {

    private val openingService by inject<OpeningService>()
    private val databaseService by inject<DatabaseService>()
    private val dslContext by inject<DSLContext>()

    @AfterEach
    fun afterEach() = runTest {
        listOf(
            OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER,
            REFERENCE_PLAYER,
        ).forEach { table ->
            dslContext
                .deleteFrom(table)
                .awaitExecute()
        }
    }

    @Test
    fun `fetchReferencePlayerNextMovesData returns per-color entries and aggregates when color is null`() = runTest {
        insertPlayer("p_test")

        // player as RED
        insertOpeningEntry("p_test", Color.RED, "h2e2", occurrences = 10, redWins = 6, blackWins = 3)
        insertOpeningEntry("p_test", Color.RED, "b2e2", occurrences = 4, redWins = 1, blackWins = 2)
        // player as BLACK
        insertOpeningEntry("p_test", Color.BLACK, "h2e2", occurrences = 5, redWins = 2, blackWins = 2)

        val red = openingService.fetchReferencePlayerNextMovesData(
            OpeningReferencePlayerNextMovesRequest(moves = emptyList(), playerId = "p_test", color = "RED")
        )
        assertEquals(listOf("h2e2", "b2e2"), red.entries.map { it.nextMove })
        assertEquals(10, red.entries.first().occurrences)

        val black = openingService.fetchReferencePlayerNextMovesData(
            OpeningReferencePlayerNextMovesRequest(moves = emptyList(), playerId = "p_test", color = "BLACK")
        )
        assertEquals(listOf("h2e2"), black.entries.map { it.nextMove })
        assertEquals(5, black.entries.single().occurrences)

        val all = openingService.fetchReferencePlayerNextMovesData(
            OpeningReferencePlayerNextMovesRequest(moves = emptyList(), playerId = "p_test", color = null)
        )
        assertEquals(listOf("h2e2", "b2e2"), all.entries.map { it.nextMove })
        // h2e2 aggregates RED (10) + BLACK (5)
        val h2e2 = all.entries.single { it.nextMove == "h2e2" }
        assertEquals(15, h2e2.occurrences)
        assertEquals((6 + 2).toFloat() / 15f, h2e2.redWinsRate)
        assertEquals((3 + 2).toFloat() / 15f, h2e2.blackWinsRate)
    }

    @Test
    fun `hasPlayerOpeningData reflects presence of opening data`() = runTest {
        insertPlayer("p_with")
        insertPlayer("p_without")
        insertOpeningEntry("p_with", Color.RED, "h2e2", occurrences = 1, redWins = 1, blackWins = 0)

        assertTrue(databaseService.hasPlayerOpeningData("p_with"))
        assertFalse(databaseService.hasPlayerOpeningData("p_without"))
    }

    private suspend fun insertPlayer(id: String) {
        dslContext.insertInto(REFERENCE_PLAYER)
            .set(REFERENCE_PLAYER.ID, id)
            .set(REFERENCE_PLAYER.SOURCE_NAME, id)
            .set(REFERENCE_PLAYER.CANONICAL_NAME, id)
            .set(REFERENCE_PLAYER.IS_VISIBLE, true)
            .awaitExecute()
    }

    private suspend fun insertOpeningEntry(
        playerId: String,
        color: Color,
        nextMove: String,
        occurrences: Int,
        redWins: Int,
        blackWins: Int,
    ) {
        dslContext.insertInto(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER)
            .set(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.REFERENCE_PLAYER_ID, playerId)
            .set(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.COLOR, color.name)
            .set(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.NUMBER_OF_MOVES, 1)
            .set(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.MOVES, nextMove)
            .set(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.OCCURRENCES, occurrences)
            .set(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.OUTCOME_RED_WINS, redWins)
            .set(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.OUTCOME_BLACK_WINS, blackWins)
            .set(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.OUTCOME_DRAWS, occurrences - redWins - blackWins)
            .awaitExecute()
    }
}
