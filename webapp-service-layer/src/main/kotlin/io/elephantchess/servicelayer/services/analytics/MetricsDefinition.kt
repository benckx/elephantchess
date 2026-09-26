package io.elephantchess.servicelayer.services.analytics

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.BotGame.BOT_GAME
import io.elephantchess.db.utils.diffInSeconds
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.services.GameDataService.Companion.MIN_MOVE_INDEX

/**
 * A guest that stayed around for at least [MIN_GENUINE_GUEST_LIFESPAN_SECONDS] between its creation and
 * its last activity. Guests that never became active (`last_online` is null) yield a null difference and
 * are therefore excluded.
 */
private val isLongLivedGuest =
    USER.USER_TYPE.eq(UserType.GUEST)
        .and(diffInSeconds(USER.LAST_ONLINE, USER.CREATION).ge(MIN_GENUINE_GUEST_LIFESPAN_SECONDS))

val allMetrics: List<Metric> by lazy {
    listOf(
        DateTimeCountMetric(
            "new users",
            USER,
            USER.CREATION,
            USER.USER_TYPE.eq(UserType.AUTHENTICATED)
        ),
        CompositeSumMetric(
            "new guests",
            DateTimeCountMetric(
                "new guests",
                USER,
                USER.CREATION,
                isLongLivedGuest
            ),
            listOf(
                DaySumMetric(
                    "archived new guests",
                    ARCHIVED_GUEST_DAILY,
                    ARCHIVED_GUEST_DAILY.DAY,
                    ARCHIVED_GUEST_DAILY.GUESTS_UNDER_5MIN
                        .plus(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_15MIN)
                        .plus(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_30MIN)
                        .plus(ARCHIVED_GUEST_DAILY.GUESTS_OTHER)
                )
            )
        ),
        DaySumMetric(
            "archived guests",
            ARCHIVED_GUEST_DAILY,
            ARCHIVED_GUEST_DAILY.DAY,
            ARCHIVED_GUEST_DAILY.GUESTS_UNDER_30MIN
                .plus(ARCHIVED_GUEST_DAILY.GUESTS_OTHER)
        ),
        TotalPuzzleMetric(),
        DateTimeCountMetric(
            "puzzle votes",
            PUZZLE_RESULT,
            PUZZLE_RESULT.ENTRY_CREATION,
            PUZZLE_RESULT.UP_VOTED.isNotNull
        ),
        DateTimeCountMetric(
            "PvP",
            GAME,
            GAME.CREATED
        ),
        DateTimeCountMetric(
            "PvP > 3",
            GAME,
            GAME.CREATED,
            GAME.CURRENT_HALF_MOVE_INDEX.ge(MIN_MOVE_INDEX)
        ),
        DateTimeCountMetric(
            "PvB",
            BOT_GAME,
            BOT_GAME.CREATED
        ),
        DateTimeCountMetric(
            "PvB > 3",
            BOT_GAME,
            BOT_GAME.CREATED,
            BOT_GAME.CURRENT_HALF_MOVE_INDEX.ge(MIN_MOVE_INDEX)
        ),
        DateTimeCountMetric(
            "newsletters",
            NEWSLETTER_EMAIL,
            NEWSLETTER_EMAIL.SENT_TIME,
            NEWSLETTER_EMAIL.SENT_TIME.isNotNull
        ),
        DateTimeCountMetric(
            "chat msg",
            GAME_CHAT_MESSAGE,
            GAME_CHAT_MESSAGE.MESSAGE_TIME
        ),
        DateTimeCountMetric(
            "db searches",
            REFERENCE_GAME_SEARCH_QUERY,
            REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME
        ),
        DateTimeCountMetric(
            "pw recoveries",
            PASSWORD_RECOVERY_ATTEMPT,
            PASSWORD_RECOVERY_ATTEMPT.ENTRY_CREATION
        ),
        DaySumMetric(
            "ads cost",
            GOOGLE_ADS_SPENDING,
            GOOGLE_ADS_SPENDING.DAY,
            GOOGLE_ADS_SPENDING.COST
        ),
        DaySumMetric(
            "ads clicks",
            GOOGLE_ADS_SPENDING,
            GOOGLE_ADS_SPENDING.DAY,
            GOOGLE_ADS_SPENDING.CLICKS
        ),
    )
}
