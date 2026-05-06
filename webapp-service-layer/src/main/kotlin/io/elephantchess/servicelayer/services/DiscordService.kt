package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.Game
import io.elephantchess.db.services.DiscordGameNotificationDaoService
import io.elephantchess.model.DiscordNotificationEventType.GAME_CREATED
import io.elephantchess.servicelayer.clients.DiscordClient
import io.elephantchess.servicelayer.clients.dto.DiscordMessage
import io.elephantchess.xiangqi.Color
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class DiscordService(
    private val discordClient: DiscordClient,
    private val userCache: UserCache,
    private val daoService: DiscordGameNotificationDaoService,
    appConfig: AppConfig,
    private val logger: KLogger,
) {

    private val webHost = appConfig.webHost
    private val suffix = appConfig.loadString("discord.suffix")
    private val isEnabled = appConfig.loadBoolean("discord.notification.enabled", false)

    private val discordServiceScope by lazy { CoroutineScope(Dispatchers.IO) }

    init {
        if (isEnabled) {
            logger.info { "discord notifications are enabled" }
        } else {
            logger.info { "discord notifications are disabled " }
        }
    }

    fun gameCreated(userId: String, game: Game) = doAsync {
        fun gameLink(channelId: String, game: Game) =
            "${webHost}/game?id=${game.id}&channelId=$channelId"

        val username = userCache.fetchUsername(userId)

        newGameNotificationChannels.forEach { channelId ->
            val content = "$username (${game.inviterRatingFrom}) created a game ${formatGameSettings(game)} " +
                    "that you can join at ${gameLink(channelId, game)} $suffix"

            logExceptionIfAny {
                val response = discordClient.sendMessage(channelId, DiscordMessage(content))
                daoService.insertGameCreated(
                    userId = userId,
                    gameId = game.id,
                    channelId = channelId,
                    messageId = response.id,
                    sent = parseTimestamp(response.timestamp)
                )
            }
        }
    }

    fun gameJoined(gameId: String) = doAsync {
        newGameNotificationChannels.forEach { channelId ->
            logExceptionIfAny {
                addReactionToOriginalMessage(channelId, gameId, HAS_JOINED_REACTION)
            }
        }
    }

    fun gameCanceled(gameId: String) = doAsync {
        newGameNotificationChannels.forEach { channelId ->
            logExceptionIfAny {
                addReactionToOriginalMessage(channelId, gameId, HAS_CANCELED_REACTION)
            }
        }
    }

    fun gameReachedMove3(gameId: String) = doAsync {
        newGameNotificationChannels.forEach { channelId ->
            logExceptionIfAny {
                addReactionToOriginalMessage(channelId, gameId, HAS_REACHED_MOVE_3)
            }
        }
    }

    private suspend fun addReactionToOriginalMessage(channelId: String, gameId: String, emoji: String) {
        val gameCreatedNotification = daoService.findGameCreatedNotification(channelId, gameId)
        if (gameCreatedNotification != null) {
            discordClient.addReaction(
                channelId = channelId,
                messageId = gameCreatedNotification.messageId,
                emoji = emoji
            )
        } else {
            logger.error { "original $GAME_CREATED notification event not found for (channel $channelId, game $gameId); can not add reaction" }
        }
    }

    private suspend fun logExceptionIfAny(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.error(e) { "error in discord service" }
        }
    }

    private fun doAsync(block: suspend () -> Unit) {
        if (isEnabled) {
            discordServiceScope.launch {
                block()
            }
        } else {
            logger.warn { "discord notifications are disabled" }
        }
    }

    private companion object {

        const val HAS_JOINED_REACTION = "✅"
        const val HAS_CANCELED_REACTION = "❌"
        const val HAS_REACHED_MOVE_3 = "3\uFE0F⃣"

        const val ELEPHANT_CHESS_NEW_GAME_CHANNEL_ID = "1320703368473215019"
        val newGameNotificationChannels = listOf(ELEPHANT_CHESS_NEW_GAME_CHANNEL_ID)

        fun parseTimestamp(timestamp: String): Instant =
            java.time.Instant.parse(timestamp).toKotlinInstant()

        fun formatGameSettings(game: Game): String {
            fun formatRated(game: Game) =
                if (game.isRated) "rated" else "casual"

            fun formatColor(game: Game) =
                when (game.inviterColor) {
                    Color.RED -> "red"
                    Color.BLACK -> "black"
                    else -> "any color"
                }

            fun formatTimeControl(game: Game): String {
                val base = when {
                    game.timeControlBase < 3600 -> "${game.timeControlBase / 60}m"
                    game.timeControlBase < 86400 -> "${game.timeControlBase / 3600}h"
                    else -> "${game.timeControlBase / 86400}d"
                }

                val increment =
                    if (game.timeControlIncrement != null) {
                        " +${game.timeControlIncrement}s"
                    } else {
                        ""
                    }

                return "$base$increment"
            }

            return "(${formatTimeControl(game)}, playing ${formatColor(game)}, ${formatRated(game)})"
        }

    }

}
