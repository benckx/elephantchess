package io.elephantchess.servicelayer.clients

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.clients.ApiLayerClient.Companion.Service.IP_TO_LOCATION
import io.elephantchess.servicelayer.clients.ApiLayerClient.Companion.Service.USER_AGENT
import io.elephantchess.servicelayer.clients.dto.LocationResponse
import io.elephantchess.servicelayer.clients.dto.UserAgentResponse
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.random.Random.Default.nextDouble
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

class ApiLayerClient(
    appConfig: AppConfig,
    private val logger: KLogger = logger {},
) {

    private val apiKey by lazy { appConfig.apiLayerApiKey }
    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout)
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    private val lastErrors = mutableMapOf<Service, List<Instant>>()

    suspend fun parseUserAgent(userAgent: String): UserAgentResponse? {
        if (isAvailable(USER_AGENT)) {
            val url = "$BASE_URL/user_agent/parse?ua=${encodeParam(userAgent)}"
            return makeAndHandleGetRequest<UserAgentResponse>(url, USER_AGENT)
        } else {
            if (nextDouble() <= (1 / 50.0)) {
                logger.warn { "$USER_AGENT service is not available" }
            }

            return null
        }
    }

    suspend fun fetchGeographicData(ip: String): LocationResponse? {
        if (isAvailable(IP_TO_LOCATION)) {
            val url = "$BASE_URL/ip_to_location/$ip"
            return makeAndHandleGetRequest<LocationResponse>(url, IP_TO_LOCATION)
        } else {
            if (nextDouble() <= (1 / 50.0)) {
                logger.warn { "$IP_TO_LOCATION service is not available" }
            }
            return null
        }

    }

    private suspend inline fun <reified T> makeAndHandleGetRequest(url: String, service: Service): T? {
        val statement = client.prepareGet(url) {
            timeout { requestTimeoutMillis = 30_000 }
            headers { append("apikey", apiKey) }
        }

        return handleHttpStatement(statement, service)
    }

    private suspend inline fun <reified T> handleHttpStatement(httpStatement: HttpStatement, service: Service): T? {
        var response: HttpResponse? = null

        try {
            response = httpStatement.execute()
        } catch (e: Exception) {
            logger.error { "error calling $httpStatement -> $e" }
            lastErrors[service] = lastErrors[service]?.plus(Instant.now()) ?: listOf(Instant.now())
        }

        if (response != null) {
            try {
                return response.body()
            } catch (e: Exception) {
                val body = response.bodyAsText()
                logger.error { "error parsing data from $httpStatement -> $e, body: $body" }
                lastErrors[service] = lastErrors[service]?.plus(Instant.now()) ?: listOf(Instant.now())
            }
        }

        return null
    }

    /**
     * To avoid spamming the API Layer service with requests when we are out of quota,
     * we won't send requests if we have more than 3 "API rate limit" errors in the last 12 hours.
     */
    private fun isAvailable(service: Service): Boolean {
        val now = Instant.now()
        val limit = now - ERROR_WINDOW.toJavaDuration()
        lastErrors[service] = lastErrors[service]?.filter { it > limit } ?: emptyList()
        return lastErrors[service] == null || lastErrors[service]!!.size < MAX_ERRORS
    }

    private companion object {

        const val BASE_URL = "https://api.apilayer.com"

        val ERROR_WINDOW = 12.hours
        const val MAX_ERRORS = 3

        fun encodeParam(param: String): String {
            return URLEncoder.encode(param, StandardCharsets.UTF_8)
        }

        enum class Service {
            USER_AGENT, IP_TO_LOCATION
        }

    }

}
