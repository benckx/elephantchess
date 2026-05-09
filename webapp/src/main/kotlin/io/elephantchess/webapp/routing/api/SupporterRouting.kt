package io.elephantchess.webapp.routing.api

import io.elephantchess.servicelayer.services.KofiService
import io.elephantchess.servicelayer.utils.ops.koin
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.supporterRoutes() {
    val kofiService by koin<KofiService>()

    route("/api/") {
        get("/lobby/list-latest-tippers") {
            call.respond(kofiService.listLatestTippers())
        }
        get("/lobby/list-latest-recurrent-supporters") {
            call.respond(kofiService.listLatestRecurrentSupporters())
        }
        get("/supporters/fetch-latest-tip") {
            call.respond(listOfNotNull(kofiService.fetchLatestSupporter()))
        }
    }
}
