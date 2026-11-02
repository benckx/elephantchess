package io.elephantchess.csvdump

import io.elephantchess.xiangqi.Board
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        // 9486 lines in the file minus the header line.
        assertEquals(9485, rows.size)
    }

    @Test
    fun `parseGames reads every distinct game`() {
        assertEquals(200, games.size)
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
        val game = games.first { it.gameId == "Vc04CIAc6UWQ" }

        // players
        assertEquals("LiLirzVBoGG9", game.redPlayer.name)
        assertEquals("QYTp8qCX6Xb8", game.blackPlayer.name)
        assertEquals(27.days, game.redPlayer.accountAge)
        assertEquals(UserType.GUEST, game.redPlayer.userType)

        // elo
        assertEquals(PlayerEloUpdate(before = 1111, after = 1115), game.redPlayerEloUpdate)
        assertEquals(PlayerEloUpdate(before = 898, after = 894), game.blackPlayerEloChange)

        // time control
        assertEquals(TimeControl(base = 1800.seconds, increment = 0.seconds, isPerMove = false), game.timeControl)
        assertEquals(TimeControlCategory.RAPID, game.timeControlCategory)

        // other attributes
        assertEquals(RatingMode.RATED, game.ratingMode)
        assertEquals(Outcome.WHITE_WINS, game.outcome)
        assertEquals(GameJoinSource.LINK, game.gameJoinSource)
        assertEquals(17.seconds, game.matchingTime)
        assertEquals(false, game.hasSockPuppetReport)

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
        val game = games.first { it.gameId == "Vc04CIAc6UWQ" }

        // Game starts at the first move's timestamp.
        assertEquals(Instant.parse("2026-07-07T08:31:02.894710Z"), game.startTime)

        // Red player's account age is "27d", so creation is ~27 days before the game start,
        // rounded to the nearest hour (08:31 -> 09:00).
        assertEquals(27.days, game.redPlayer.accountAge)
        assertEquals(
            Instant.parse("2026-06-10T09:00:00Z"),
            game.redPlayer.approximateAccountCreation(game.startTime),
        )
    }

    @Test
    fun `approximate account creation is null when account age is unknown`() {
        val player = Player(name = "someone", accountAge = null, userType = UserType.GUEST)
        assertNull(player.approximateAccountCreation(Instant.parse("2026-07-07T08:31:02.894710Z")))
    }

    @Test
    fun `elo update delta is the signed difference between after and before`() {
        assertEquals(4, PlayerEloUpdate(before = 1111, after = 1115).delta)
        assertEquals(-4, PlayerEloUpdate(before = 898, after = 894).delta)
        assertEquals(0, PlayerEloUpdate(before = 1000, after = 1000).delta)
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
    fun `game report and matching time come from the lowest move index`() {
        val game = PvpGame.fromCsvRows(
            listOf(
                gameRow(moveIndex = 1, matchingTime = "99", report = "false"),
                gameRow(moveIndex = 0, matchingTime = " 42 ", report = " true "),
            ),
        ).single()
        assertEquals(42.seconds, game.matchingTime)
        assertTrue(game.hasSockPuppetReport)
    }

    @Test
    fun `game without report parses false and blank matching time parses null`() {
        val game = PvpGame.fromCsvRows(listOf(gameRow(matchingTime = " ", report = "false"))).single()
        assertNull(game.matchingTime)
        assertEquals(false, game.hasSockPuppetReport)
    }

    @Test
    fun `zero matching time is not treated as missing`() {
        val game = PvpGame.fromCsvRows(listOf(gameRow(matchingTime = "0"))).single()
        assertEquals(0.seconds, game.matchingTime)
    }

    @Test
    fun `invalid report and matching time fail explicitly`() {
        listOf("", "yes").forEach { report ->
            assertFailsWith<IllegalArgumentException> {
                PvpGame.fromCsvRows(listOf(gameRow(report = report)))
            }
        }
        assertFailsWith<NumberFormatException> {
            PvpGame.fromCsvRows(listOf(gameRow(matchingTime = "invalid")))
        }
    }

    @Test
    fun `exported game metadata matches every move row`() {
        val gamesById = games.associateBy { it.gameId }
        rows.forEach { row ->
            val game = gamesById.getValue(row.value("game_id"))
            assertEquals(row.value("has_sock_puppet_report").toBooleanStrict(), game.hasSockPuppetReport)
            assertEquals(row.intOrNull("matching_time_seconds")?.seconds, game.matchingTime)
        }
    }

    @Test
    fun `parseRowsFromZip reads rows from every CSV inside the archive`() {
        val rows = PvpMovesCsvParser.parseRowsFromZip(zipPath())
        assertEquals(21375, rows.size)
    }

    @Test
    fun `parseGamesFromZip aggregates games across every CSV inside the archive`() {
        val games = PvpMovesCsvParser.parseGamesFromZip(zipPath())
        assertEquals(400, games.size)
        assertEquals(21375, games.sumOf { game -> game.moveCount })
        assertEquals(72, games.count { it.hasSockPuppetReport })
        assertEquals(328, games.count { !it.hasSockPuppetReport })
    }

    companion object {

        private fun gameRow(moveIndex: Int = 0, matchingTime: String = "42", report: String = "true"): CsvRow =
            CsvRow(
                mapOf(
                    "game_id" to "game",
                    "timestamp" to "2026-07-07T08:31:02.894710Z",
                    "move_index" to moveIndex.toString(),
                    "move" to "h2h6",
                    "red_player" to "red",
                    "black_player" to "black",
                    "red_account_age" to "",
                    "black_account_age" to "",
                    "red_user_type" to "GUEST",
                    "black_user_type" to "GUEST",
                    "red_elo_before" to "",
                    "red_elo_after" to "",
                    "black_elo_before" to "",
                    "black_elo_after" to "",
                    "time_control" to "1800+0",
                    "time_control_category" to "RAPID",
                    "rating_mode" to "rated",
                    "game_status" to "CHECKMATED",
                    "outcome" to "WHITE_WINS",
                    "game_join_source" to "LINK",
                    "move_analysis" to "",
                    "engine_analysis" to "",
                    "cpl" to "",
                    "matching_time_seconds" to matchingTime,
                    "has_sock_puppet_report" to report,
                ),
            )

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
