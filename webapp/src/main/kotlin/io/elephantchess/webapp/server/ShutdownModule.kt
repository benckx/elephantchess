package io.elephantchess.webapp.server

import io.elephantchess.servicelayer.utils.ShutdownHandler
import io.elephantchess.servicelayer.utils.ops.koin
import io.ktor.server.application.*

private val shutdownHandler by koin<ShutdownHandler>()

fun Application.shutdownModule() {
    monitor.subscribe(ApplicationStopping) {
        shutdownHandler.shutdown()
    }
}
