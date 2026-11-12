package io.elephantchess.webapp.routing.api

import io.elephantchess.servicelayer.services.GlobalAnalyticsService
import io.elephantchess.servicelayer.utils.ops.koin
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// TODO: bring other "global" calls here
fun Route.globalPageRoutes() {
    val globalAnalyticsService by koin<GlobalAnalyticsService>()

    route("/api/global") {
        get("/pvp-leaderboard") {
            call.respond(globalAnalyticsService.fetchPlayerVsPlayerLeaderboard())
        }
        get("/game-stats") {
            call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
            call.respond(globalAnalyticsService.fetchGlobalGameStats())
        }
        get("/app-data") {
            call.respond(globalAnalyticsService.fetchGlobalAppData())
        }
    }
}
