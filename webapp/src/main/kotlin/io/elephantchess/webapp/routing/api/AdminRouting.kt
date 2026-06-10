package io.elephantchess.webapp.routing.api

import io.elephantchess.servicelayer.dto.admin.CreateUpcomingEventRequest
import io.elephantchess.servicelayer.dto.admin.ToggleUpcomingEventRequest
import io.elephantchess.servicelayer.dto.admin.UpdateUpcomingEventRequest
import io.elephantchess.servicelayer.services.admin.*
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.requireAdminRole
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.net.URLDecoder

fun Route.adminConsoleRoutes() {
    route("/api/admin/analytics") {
        adminOverviewRoutes()
        adminFeedsRoutes()
        adminAnalyticsRoutes()
        adminChatRoutes()
        adminDatabaseSearchRoutes()
        adminPasswordRecoveryRoutes()
        adminUserSessionRoutes()
        adminAnalysisRoutes()
        adminPostgresRoutes()
        adminDatabaseRoutes()
        adminExceptionRoutes()
        adminNewsletterRoutes()
        adminUpcomingEventsRoutes()
    }
}

private fun Route.adminOverviewRoutes() {
    val adminOverviewService by koin<AdminOverviewService>()

    get("/online-within-hours") {
        requireAdminRole { _ ->
            val hours = call.parameters["hours"] ?: "12"
            adminOverviewService.listOnlineWithinHours(hours.toInt())
        }
    }
    get("/online-users") {
        requireAdminRole { adminOverviewService.listOnlineUsers() }
    }
    get("/latest-game-activity/latest-pvp") {
        requireAdminRole { adminOverviewService.fetchLatestPvpActivity() }
    }
    get("/latest-game-activity/latest-pvb") {
        requireAdminRole { adminOverviewService.fetchLatestPvbActivity() }
    }
    get("/latest-game-activity/live-pvp") {
        requireAdminRole { adminOverviewService.fetchLivePvpGames() }
    }
    get("/latest-game-activity/live-pvb") {
        requireAdminRole { adminOverviewService.fetchLivePvbGames() }
    }
    get("/latest-puzzle-activity") {
        requireAdminRole { adminOverviewService.fetchLatestPuzzleActivity() }
    }
    get("/latest-analysis-activity") {
        requireAdminRole { adminOverviewService.fetchLatestAnalysisActivity() }
    }
    get("/latest-new-users") {
        requireAdminRole { adminOverviewService.fetchLatestNewUsers() }
    }
}

private fun Route.adminFeedsRoutes() {
    val adminFeedService by koin<AdminFeedService>()

    get("/list-games") {
        requireAdminRole { adminFeedService.listLatestPvpGames() }
    }
    get("/list-variant-games") {
        requireAdminRole { adminFeedService.listLatestVariantPvpGames() }
    }
    get("/list-bot-games") {
        requireAdminRole { adminFeedService.listLatestPvbGames() }
    }
    get("/list-variant-bot-games") {
        requireAdminRole { adminFeedService.listLatestVariantPvbGames() }
    }
    get("/last-played-puzzles") {
        requireAdminRole { adminFeedService.listLastPuzzlePlayedByLoggedUsers() }
    }
    get("/last-users-analysis") {
        requireAdminRole { adminFeedService.listLastUsersAnalysis() }
    }
    get("/content-section-feedback") {
        requireAdminRole { adminFeedService.listLatestFeedback() }
    }
}

private fun Route.adminAnalyticsRoutes() {
    val adminAnalyticsService by koin<AdminAnalyticsService>()

    get("/hourly-stats") {
        requireAdminRole { adminAnalyticsService.fetchHourlyStats() }
    }
    get("/daily-stats") {
        requireAdminRole { adminAnalyticsService.fetchDailyStats() }
    }
    get("/monthly-stats") {
        requireAdminRole { adminAnalyticsService.fetchMonthlyStats() }
    }
    get("/pvp-join-source-stats") {
        requireAdminRole { adminAnalyticsService.fetchPvpStatsByJoinSource() }
    }
    get("/yearly-stats") {
        requireAdminRole { adminAnalyticsService.fetchYearlyStats() }
    }
    get("/total-stats") {
        requireAdminRole { adminAnalyticsService.fetchTotalStats() }
    }
    get("/daily-avg-by-month") {
        requireAdminRole { adminAnalyticsService.fetchDailyAvgByMonthStats() }
    }
    get("/analysis-per-user") {
        requireAdminRole { adminAnalyticsService.fetchAnalysisPerUser() }
    }
    get("/online-users-stats-by-hour") {
        requireAdminRole { adminAnalyticsService.fetchOnlineUsersStatsByHour() }
    }
    get("/online-users-stats-by-day") {
        requireAdminRole { adminAnalyticsService.fetchOnlineUsersStatsByDay() }
    }
    get("/online-users-stats-by-day-of-week") {
        requireAdminRole { adminAnalyticsService.fetchOnlineUsersStatsByDayOfWeek() }
    }
    get("/online-users-stats-by-month") {
        requireAdminRole { _ ->
            val months = call.parameters["months"]?.toIntOrNull() ?: 12
            adminAnalyticsService.fetchOnlineUsersStatsByMonth(months)
        }
    }
    get("/page-view-stats-by-event-path") {
        requireAdminRole { _ ->
            val encodedPath = call.parameters["path"] ?: "/"
            val eventPath = URLDecoder.decode(encodedPath, "UTF-8")

            adminAnalyticsService.fetchPageViewStatsByEventPath(eventPath)
        }
    }
    get("/page-view-stats-database-games") {
        requireAdminRole { _ ->
            adminAnalyticsService.fetchPageViewStatsForDatabaseGames()
        }
    }
    get("/page-view-stats-user-profiles-own") {
        requireAdminRole { _ ->
            adminAnalyticsService.fetchPageViewStatsForOwnUserProfiles()
        }
    }
    get("/page-view-stats-user-profiles-other") {
        requireAdminRole { _ ->
            adminAnalyticsService.fetchPageViewStatsForOtherUserProfiles()
        }
    }
    get("/page-view-stats-gad") {
        requireAdminRole { _ ->
            adminAnalyticsService.fetchPageViewStatsByGad()
        }
    }
    get("/hourly-page-views") {
        requireAdminRole { _ ->
            val hours = call.parameters["hours"]?.toIntOrNull() ?: 24
            adminAnalyticsService.fetchHourlyPageViews(hours)
        }
    }
    get("/daily-page-views") {
        requireAdminRole { _ ->
            val days = call.parameters["days"]?.toIntOrNull() ?: 30
            adminAnalyticsService.fechDailyPageViews(days)
        }
    }
}

private fun Route.adminChatRoutes() {
    val adminChatService by koin<AdminChatService>()

    get("/last-chat-messages") {
        requireAdminRole { adminChatService.listLastChatMessages() }
    }
}

private fun Route.adminDatabaseSearchRoutes() {
    val adminDatabaseSearchService by koin<AdminDatabaseSearchService>()

    get("/list-latest-search-queries") {
        requireAdminRole { adminDatabaseSearchService.listLatestSearchQueries() }
    }
}

private fun Route.adminPasswordRecoveryRoutes() {
    val adminPasswordRecoveryService by koin<AdminPasswordRecoveryService>()

    get("/list-latest-password-recovery-attempts") {
        requireAdminRole { adminPasswordRecoveryService.listLatestPasswordRecoveryAttempts() }
    }
}

private fun Route.adminUserSessionRoutes() {
    val adminUserSessionService by koin<AdminUserSessionService>()
    get("/list-user-sessions") {
        requireAdminRole { adminUserSessionService.listAuthenticatedUserSessions() }
    }
}

private fun Route.adminAnalysisRoutes() {
    val adminAnalysisService by koin<AdminAnalysisService>()

    get("/list-latest-move-analysis-by-game") {
        requireAdminRole { adminAnalysisService.listLatestMoveAnalysisByGame() }
    }
    get("/pre-analyzed-reference-games-per-year") {
        requireAdminRole { adminAnalysisService.listPreAnalyzedReferenceGamesPerYear() }
    }
    get("/list-games-analyzed-from-batch") {
        requireAdminRole { adminAnalysisService.listGamesAnalyzedFromBatch() }
    }
}

private fun Route.adminPostgresRoutes() {
    val adminPostgresService by koin<AdminPostgresService>()

    get("/database-table-sizes") {
        requireAdminRole { adminPostgresService.fetchTableSizes() }
    }
}

private fun Route.adminDatabaseRoutes() {
    val adminDatabaseService by koin<AdminDatabaseService>()

    get("/latest-player-profile-versions") {
        requireAdminRole { _ ->
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 100
            adminDatabaseService.listLatestPlayerProfileVersions(limit)
        }
    }
}

private fun Route.adminExceptionRoutes() {
    val adminExceptionService by koin<AdminExceptionService>()

    get("/list-latest-thrown-exceptions") {
        requireAdminRole { _ ->
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 200
            val httpCodeFilter = call.parameters["codeFilter"]
            adminExceptionService.listLatestExceptions(limit, httpCodeFilter)
        }
    }
}

private fun Route.adminNewsletterRoutes() {
    val adminNewsletterService by koin<AdminNewsletterService>()

    get("/newsletter-stats") {
        requireAdminRole { adminNewsletterService.fetchNewsletterStats() }
    }
}

private fun Route.adminUpcomingEventsRoutes() {
    val adminUpcomingEventsService by koin<AdminUpcomingEventsService>()

    get("/upcoming-events") {
        requireAdminRole { adminUpcomingEventsService.listAllEvents() }
    }
    post("/upcoming-events") {
        requireAdminRole { authenticatedToken ->
            val request = call.receive<CreateUpcomingEventRequest>()
            adminUpcomingEventsService.createEvent(authenticatedToken.userId, request)
        }
    }
    put("/upcoming-events") {
        requireAdminRole {
            val request = call.receive<UpdateUpcomingEventRequest>()
            adminUpcomingEventsService.updateEvent(request)
        }
    }
    post("/upcoming-events/toggle") {
        requireAdminRole {
            val request = call.receive<ToggleUpcomingEventRequest>()
            adminUpcomingEventsService.toggleEnabled(request)
        }
    }
}
