package io.elephantchess.webapp.routing

import io.elephantchess.servicelayer.services.GlobalAnalyticsService
import io.elephantchess.servicelayer.utils.ops.koin
import io.ktor.server.response.*
import io.ktor.server.routing.*

// TODO: bring other "global" calls here
private val globalAnalyticsService by koin<GlobalAnalyticsService>()

fun Route.globalPageRoutes() {
    route("/api/global") {
        get("/pvp-leaderboard") {
            call.respond(globalAnalyticsService.fetchPlayerVsPlayerLeaderboard())
        }
        get("/game-stats") {
            call.respond(globalAnalyticsService.fetchGlobalGameStats())
        }
        get("/app-data") {
            call.respond(globalAnalyticsService.fetchGlobalAppData())
        }
    }
}
