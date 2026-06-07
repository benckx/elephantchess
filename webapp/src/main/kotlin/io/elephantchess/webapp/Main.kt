package io.elephantchess.webapp

import io.elephantchess.config.ArgConfig.Companion.parseArgs
import io.elephantchess.servicelayer.serviceLayerModule
import io.elephantchess.webapp.routing.html.htmlRoutingModule
import io.elephantchess.webapp.routing.sitemapRoutingModule
import io.elephantchess.webapp.routing.wellKnownRoutingModule
import io.elephantchess.webapp.server.*
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.core.context.startKoin

private val logger = logger {}

fun main(args: Array<String>) {
    val argsConfig = parseArgs(args)
    logger.info { "args: ${args.joinToString(" ")}" }
    logger.info { "starting with $argsConfig" }

    // dependency injection
    startKoin {
        modules(
            serviceLayerModule(
                argConfig = argsConfig,
                eagerAllowed = true
            ),
            webAppKoinModule(eagerAllowed = true)
        )
    }

    // Ktor server
    embeddedServer(factory = Netty, port = 8080, module = Application::kTorModule)
        .start(wait = true)
}

private fun Application.kTorModule() {
    configureDefaultHeaders()
    exceptionHandler()
    cachingModule()
    staticAssetsModule()
    apiServiceModule()
    htmlRoutingModule()
    sitemapRoutingModule()
    wellKnownRoutingModule()
    shutdownModule()
    healthCheckModule()
}
