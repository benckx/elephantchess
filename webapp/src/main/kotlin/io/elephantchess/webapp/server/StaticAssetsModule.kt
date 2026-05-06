package io.elephantchess.webapp.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.routing.*

// all files in "public", except for HTML
fun Application.staticAssetsModule() {
    routing {
        staticResources("/css", "public/css")
        staticResources("/js", "public/js")
        staticResources("/images", "public/images")
        staticResources("/audio", "public/audio")
        staticResources("/", "public", "robots.txt")
        staticResources("/", "public", "favicon.ico")
    }
    install(Compression) {
        gzip {
            matchContentType(
                ContentType.Text.Html,
                ContentType.Text.CSS,
                ContentType.Text.JavaScript,
                ContentType.Text.Xml,
                ContentType.Application.JavaScript,
                ContentType.Application.Xml,
                ContentType.Image.SVG,
            )
            minimumSize(1_024)
        }
    }
}
