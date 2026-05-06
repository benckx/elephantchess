package io.elephantchess.webapp.routing

import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.sitemap.SiteMapService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Configures the dynamic sitemap.xml endpoint.
 */
fun Application.sitemapRoutingModule() {
    val siteMapService by koin<SiteMapService>()
    val logger = KotlinLogging.logger {}

    routing {
        route("/sitemap.xml") {
            get {
                logger.info { "sitemap.xml GET requested" }
                call.respondText(
                    text = siteMapService.getSiteMapXml(),
                    contentType = ContentType.Application.Xml,
                    status = HttpStatusCode.OK
                )
            }
            head {
                logger.info { "sitemap.xml HEAD requested" }
                val xml = siteMapService.getSiteMapXml()
                call.response.status(HttpStatusCode.OK)
                call.response.header(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
                call.response.header(HttpHeaders.ContentLength, xml.length.toString())
            }
        }
    }
}
