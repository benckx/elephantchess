package io.elephantchess.csvdump

import kotlin.time.Instant

/**
 * A whole PvP game reconstructed from the CSV rows sharing the same `game_id`. Game-level
 * metadata is taken from the first (lowest `moveIndex`) row; [moves] holds every half move
 * played, ordered by [PvpMove.moveIndex].
 */
data class PvpGame(
    val gameId: String,
    val redPlayer: Player,
    val blackPlayer: Player,
    val redPlayerEloUpdate: PlayerEloUpdate?,
    val blackPlayerEloChange: PlayerEloUpdate?,
    val timeControl: TimeControl,
    val timeControlCategory: TimeControlCategory,
    val ratingMode: RatingMode,
    val gameStatus: String,
    val outcome: Outcome?,
    val gameJoinSource: GameJoinSource?,
    val moves: List<PvpMove>,
) {

    val moveCount: Int get() = moves.size

    /** Timestamp of the first half move, used as the game's start time. */
    val startTime: Instant get() = moves.first().timestamp

    companion object {

        /**
         * Groups the given CSV rows by game id and turns each group into a [PvpGame], preserving
         * the order in which games first appear in [rows]. Game-level fields are read from the
         * row with the lowest `move_index` in each group.
         */
        fun fromCsvRows(rows: List<CsvRow>): List<PvpGame> =
            rows
                .groupBy { row -> row.value("game_id") }
                .map { (gameId, gameRows) ->
                    val moves = gameRows
                        .map { row -> PvpMove.fromCsvRow(row) }
                        .sortedBy { move -> move.moveIndex }
                    val first = gameRows.minByOrNull { row -> row.value("move_index").trim().toInt() }!!
                    PvpGame(
                        gameId = gameId,
                        redPlayer = Player(
                            name = first.value("red_player"),
                            accountAge = Player.parseAccountAge(first.value("red_account_age")),
                            userType = UserType.fromCsv(first.value("red_user_type")),
                        ),
                        blackPlayer = Player(
                            name = first.value("black_player"),
                            accountAge = Player.parseAccountAge(first.value("black_account_age")),
                            userType = UserType.fromCsv(first.value("black_user_type")),
                        ),
                        redPlayerEloUpdate = PlayerEloUpdate.of(
                            first.intOrNull("red_elo_before"),
                            first.intOrNull("red_elo_after")
                        ),
                        blackPlayerEloChange = PlayerEloUpdate.of(
                            first.intOrNull("black_elo_before"),
                            first.intOrNull("black_elo_after")
                        ),
                        timeControl = TimeControl.fromCsv(first.value("time_control")),
                        timeControlCategory = TimeControlCategory.fromCsv(first.value("time_control_category")),
                        ratingMode = RatingMode.fromCsv(first.value("rating_mode")),
                        gameStatus = first.value("game_status"),
                        outcome = Outcome.fromCsvOrNull(first.value("outcome")),
                        gameJoinSource = GameJoinSource.fromCsvOrNull(first.value("game_join_source")),
                        moves = moves,
                    )
                }
    }

}
