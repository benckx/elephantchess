package io.elephantchess.webapp.routing

import io.elephantchess.servicelayer.services.KofiService
import io.elephantchess.servicelayer.utils.ops.koin
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun Routing.integrationRoutes() {
    val kofiService by koin<KofiService>()

    get("/api/integration/kofi") {
        logger.info { "Ko-fi webhook health check accessed" }
        call.respond(
            OK,
            mapOf("status" to "Ko-fi webhook endpoint is active", "timestamp" to System.currentTimeMillis())
        )
    }
    post("/api/integration/kofi") {
        try {
            val parameters = call.receiveParameters()
            val dataJson = parameters["data"] ?: run {
                logger.warn { "Ko-fi webhook received WITHOUT 'data' parameter!" }
                logger.warn { "All parameters: ${parameters.entries().joinToString { "${it.key}=${it.value}" }}" }
                call.respond(BadRequest, "Missing 'data' parameter")
                return@post
            }

            logger.info { "raw Ko-fi JSON data: $dataJson" }
            kofiService.processEvent(json.decodeFromString(dataJson))
            call.respond(OK, "Webhook received")
        } catch (e: Exception) {
            logger.error(e) { "❌ ERROR processing Ko-fi webhook: ${e.message}" }
            logger.error { "Stack trace: ${e.stackTraceToString()}" }
            call.respond(InternalServerError, "Error processing webhook: ${e.message}")
        }
    }
}
