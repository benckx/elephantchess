package io.elephantchess.webapp.server

import io.elephantchess.webapp.routing.*
import io.elephantchess.webapp.routing.api.adminConsoleRoutes
import io.elephantchess.webapp.routing.api.analysisRoutes
import io.elephantchess.webapp.routing.api.botGameRoutes
import io.elephantchess.webapp.routing.api.botGameWsRoutes
import io.elephantchess.webapp.routing.api.databaseRoutes
import io.elephantchess.webapp.routing.api.gameDataRoutes
import io.elephantchess.webapp.routing.api.globalPageRoutes
import io.elephantchess.webapp.routing.api.lobbyRoutes
import io.elephantchess.webapp.routing.api.puzzleRoutes
import io.elephantchess.webapp.routing.api.pvpGameRoutes
import io.elephantchess.webapp.routing.api.pvpGameWsRoutes
import io.elephantchess.webapp.routing.api.sevenKingdomsGameRoutes
import io.elephantchess.webapp.routing.api.supporterRoutes
import io.elephantchess.webapp.routing.api.userRoutes
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun Application.apiServiceModule() {
    install(ContentNegotiation) {
        jackson()
    }
    install(WebSockets) {
        pingPeriod = 20.seconds
        timeout = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        pvpGameRoutes()
        pvpGameWsRoutes()
        botGameRoutes()
        botGameWsRoutes()
        sevenKingdomsGameRoutes()
        puzzleRoutes()
        databaseRoutes()
        gameDataRoutes()
        analysisRoutes()
        globalPageRoutes()
        userRoutes()
        lobbyRoutes()
        supporterRoutes()
        adminConsoleRoutes()
        newsletterUnsubscriptionRoutes()
        integrationRoutes()
    }
}
