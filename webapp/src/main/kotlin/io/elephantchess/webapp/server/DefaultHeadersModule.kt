package io.elephantchess.webapp.server

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureDefaultHeaders() {
    install(DefaultHeaders) {
        header("X-Frame-Options", "deny")
        header("Server", "elephantchess.io")
    }
}
