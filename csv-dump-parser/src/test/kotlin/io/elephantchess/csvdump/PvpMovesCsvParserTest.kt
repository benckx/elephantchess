package io.elephantchess.csvdump

import io.elephantchess.xiangqi.Board
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PvpMovesCsvParserTest {

    @Test
    fun `parseRows reads every data row of the CSV`() {
        // 7669 lines in the file minus the header line.
        assertEquals(7668, rows.size)
    }

    @Test
    fun `parseGames reads every distinct game`() {
        assertEquals(167, games.size)
    }

    @Test
    fun `parseGames aggregates all moves under their game`() {
        assertTrue(games.isNotEmpty())
        games.forEach { game ->
            assertTrue(game.moves.isNotEmpty())
            assertEquals(game.moveCount, game.moves.size)
            // Replaying every move on a fresh board proves the moves are consistent and legal:
            // registerMove throws on wrong turn color or an impossible move.
            val board = Board()
            game.moves.forEach { move -> board.registerMove(move.move) }
        }
        // Total moves across games equals the number of CSV data rows.
        assertEquals(rows.size, games.sumOf { game -> game.moveCount })
    }

    @Test
    fun `each game's moves are ordered by move index starting at zero`() {
        games.forEach { game ->
            val indices = game.moves.map { move -> move.moveIndex }
            assertEquals(indices.sorted(), indices)
            assertEquals(0, game.moves.first().moveIndex)
        }
    }

    @Test
    fun `a known game exposes its metadata, players and parsed moves`() {
        val game = games.first { it.gameId == "BhBxaEr3QaEp" }

        // players
        assertEquals("xV3gi1vIi8f5", game.redPlayer.name)
        assertEquals("9hJAL2t7Xodm", game.blackPlayer.name)
        assertEquals(27.days, game.redPlayer.accountAge)
        assertEquals(UserType.GUEST, game.redPlayer.userType)

        // elo
        assertEquals(PlayerEloChange(before = 1111, after = 1115), game.redElo)
        assertEquals(PlayerEloChange(before = 898, after = 894), game.blackElo)

        // time control
        assertEquals(TimeControl(base = 1800.seconds, increment = 0.seconds, isPerMove = false), game.timeControl)
        assertEquals(TimeControlCategory.RAPID, game.timeControlCategory)

        // other attributes
        assertEquals(RatingMode.RATED, game.ratingMode)
        assertEquals(Outcome.RED_WINS, game.outcome)
        assertEquals(GameJoinSource.LINK, game.gameJoinSource)

        val firstMove = game.moves.first()
        assertEquals("h2h6", firstMove.moveUci)
        assertEquals(-39, firstMove.cpl)

        // analysis
        val moveAnalysis = firstMove.moveAnalysis
        assertNotNull(moveAnalysis)
        assertEquals(20, moveAnalysis.depth)
        assertEquals(23, moveAnalysis.cp)
        assertEquals("h9g7", moveAnalysis.pv.first())

        assertNotNull(firstMove.engineAnalysis)
    }

    @Test
    fun `approximate account creation subtracts account age from the game start`() {
        val game = games.first { it.gameId == "BhBxaEr3QaEp" }

        // Game starts at the first move's timestamp.
        assertEquals(Instant.parse("2026-07-07T08:31:02.894710Z"), game.startTime)

        // Red player's account age is "27d", so creation is 27 days before the game start.
        assertEquals(27.days, game.redPlayer.accountAge)
        assertEquals(
            Instant.parse("2026-06-10T08:31:02.894710Z"),
            game.redPlayer.approximateAccountCreation(game.startTime),
        )
    }

    @Test
    fun `approximate account creation is null when account age is unknown`() {
        val player = Player(name = "someone", accountAge = null, userType = UserType.GUEST)
        assertNull(player.approximateAccountCreation(Instant.parse("2026-07-07T08:31:02.894710Z")))
    }

    @Test
    fun `time control is parsed for increment and per-move formats`() {
        val increment = TimeControl.fromCsv("900+10")
        assertEquals(15.minutes, increment.base)
        assertEquals(10.seconds, increment.increment)
        assertEquals(false, increment.isPerMove)

        val perMove = TimeControl.fromCsv("604800/move")
        assertEquals(7.days, perMove.base)
        assertNull(perMove.increment)
        assertEquals(true, perMove.isPerMove)
    }

    @Test
    fun `parseRowsFromZip reads rows from every CSV inside the archive`() {
        val rows = PvpMovesCsvParser.parseRowsFromZip(zipPath())
        assertEquals(20177, rows.size)
    }

    @Test
    fun `parseGamesFromZip aggregates games across every CSV inside the archive`() {
        val games = PvpMovesCsvParser.parseGamesFromZip(zipPath())
        assertEquals(400, games.size)
        assertEquals(20177, games.sumOf { game -> game.moveCount })
    }

    companion object {

        private fun csvPath(): String =
            File(
                PvpMovesCsvParserTest::class.java.getResource("/pvp_game_moves_xiangqi_059.csv")
                    ?.toURI() ?: error("test CSV resource not found"),
            ).path

        private fun zipPath(): String =
            File(
                PvpMovesCsvParserTest::class.java.getResource("/pvp_games_dump.zip")
                    ?.toURI() ?: error("test ZIP resource not found"),
            ).path

        // Parsed once and shared across the whole suite to avoid re-reading the CSV per test.
        private val rows = PvpMovesCsvParser.parseRows(csvPath())
        private val games = PvpMovesCsvParser.parseGames(csvPath())
    }

}
