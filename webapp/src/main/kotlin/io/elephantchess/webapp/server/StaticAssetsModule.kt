package io.elephantchess.webapp.server

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.utils.ops.koin
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.content.CachingOptions
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.compression.*
import io.ktor.server.routing.*

private val appConfig by koin<AppConfig>()
private val logger = KotlinLogging.logger {}

/**
 * All files in "public", except for HTML
 * However those are served from a CDN in production
 */
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
    cachingModule()
}

private fun Application.cachingModule() {
    if (appConfig.isCachingEnabled) {
        val shortCache = cachingOptionsInMinutes(30)
        val longCache = cachingOptionsInHours(24)

        install(CachingHeaders) {
            options { _, content ->
                when (content.contentType?.withoutParameters()) {
                    ContentType.Text.CSS -> shortCache
                    ContentType.Text.JavaScript -> shortCache
                    ContentType.Application.JavaScript -> shortCache
                    ContentType.Image.PNG -> longCache
                    ContentType.Image.JPEG -> longCache
                    ContentType.Image.SVG -> longCache
                    ContentType.Image.GIF -> longCache
                    ContentType.Image.XIcon -> longCache
                    ContentType.Audio.MPEG -> longCache
                    ContentType.Audio.MP4 -> longCache
                    ContentType.Application.Json -> noStore()
                    else -> null
                }
            }
        }
    } else {
        logger.warn { "asset caching is disabled" }
    }
}

@Suppress("SameParameterValue")
private fun cachingOptionsInHours(hours: Int) =
    cachingOptionsInMinutes(hours * 60)

private fun cachingOptionsInMinutes(minutes: Int) =
    CachingOptions(
        CacheControl.MaxAge(
            visibility = CacheControl.Visibility.Public,
            maxAgeSeconds = minutes * 60,
            proxyMaxAgeSeconds = minutes * 60,
        ),
    )

private fun noStore() =
    CachingOptions(
        CacheControl.NoStore(CacheControl.Visibility.Public),
    )
