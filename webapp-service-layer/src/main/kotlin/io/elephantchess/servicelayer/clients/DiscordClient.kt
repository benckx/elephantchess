package io.elephantchess.servicelayer.clients

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.clients.dto.DiscordMessage
import io.elephantchess.servicelayer.clients.dto.DiscordMessageResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class DiscordClient(appConfig: AppConfig) {

    private val token by lazy { appConfig.loadString("discord.token") }

    private val client = HttpClient(CIO) {
        install(Logging) {
            level = LogLevel.INFO
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun sendMessage(channelId: String, message: DiscordMessage): DiscordMessageResponse {
        val response =
            client.post(messageApi(channelId)) {
                tokenAndContentType()
                setBody(message)
            }

        return response.body<DiscordMessageResponse>()
    }

    suspend fun addReaction(channelId: String, messageId: String, emoji: String): String {
        val response =
            client.put("${messageApi(channelId)}/$messageId/reactions/$emoji/@me") {
                tokenAndContentType()
            }

        return response.bodyAsText()
    }

    private fun HttpMessageBuilder.tokenAndContentType() {
        headers {
            append("Authorization", "Bot $token")
            append("Content-Type", "application/json")
        }
    }

    private fun messageApi(channelId: String): String =
        "$BASE_URL/channels/$channelId/messages"

    private companion object {

        const val BASE_URL = "https://discord.com/api"

    }

}
