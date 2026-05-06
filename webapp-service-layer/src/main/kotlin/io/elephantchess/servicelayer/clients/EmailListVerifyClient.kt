package io.elephantchess.servicelayer.clients

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.clients.dto.CreditResponse
import io.elephantchess.servicelayer.clients.dto.EmailVerificationResult
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class EmailListVerifyClient(
    appConfig: AppConfig,
    private val logger: KLogger,
) {

    private val apiKey by lazy { appConfig.loadString("emaillistverify.apikey") }

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    fun verifyEmailDetailed(email: String): EmailVerificationResult? {
        return runBlocking {
            try {
                val response = client.get("$API/verifyEmailDetailed") {
                    tokenAndContentType()
                    url {
                        parameters.append("email", email)
                    }
                }

                if (response.status.value == 200) {
                    response.body<EmailVerificationResult>()
                } else {
                    response.bodyAsText().let { body ->
                        logger.warn { "status ${response.status.value}, response: $body" }
                    }
                    null
                }
            } catch (e: Exception) {
                logger.error(e) { "error verifying email: $email" }
                null
            }
        }
    }

    suspend fun getCredits(): CreditResponse? {
        return try {
            val response = client.get("$API/credits") {
                tokenAndContentType()
            }

            if (response.status.value == 200) {
                // get raw response text first for debugging
                val rawBody = response.bodyAsText()

                // try to parse the response
                try {
                    json.decodeFromString<CreditResponse>(rawBody)
                } catch (e: Exception) {
                    logger.error(e) { "failed to deserialize credits response. Raw body was: $rawBody" }
                    null
                }
            } else {
                response.bodyAsText().let { rawBody ->
                    logger.warn { "status ${response.status.value}, response: $rawBody" }
                }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "error getting credits" }
            null
        }
    }

    private fun HttpMessageBuilder.tokenAndContentType() {
        headers {
            append("x-api-key", apiKey)
            append("Content-Type", "application/json")
        }
    }

    private companion object {

        const val API = "https://api.emaillistverify.com/api"
        const val TIMEOUT = 60_000L

    }

}
