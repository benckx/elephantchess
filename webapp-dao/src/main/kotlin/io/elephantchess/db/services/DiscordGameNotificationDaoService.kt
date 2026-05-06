package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.DISCORD_GAME_NOTIFICATION
import io.elephantchess.db.dao.codegen.tables.daos.DiscordGameNotificationDao
import io.elephantchess.db.dao.codegen.tables.pojos.DiscordGameNotification
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.insertReactive
import io.elephantchess.model.DiscordNotificationEventType.GAME_CREATED
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Instant

class DiscordGameNotificationDaoService(
    private val dslContext: DSLContext
) {

    suspend fun insertGameCreated(
        userId: String,
        gameId: String,
        channelId: String,
        messageId: String,
        sent: Instant,
    ) {
        dslContext.transactionCoroutine { cfg ->
            val record = DiscordGameNotification()
            record.eventType = GAME_CREATED
            record.userId = userId
            record.gameId = gameId
            record.channelId = channelId
            record.messageId = messageId
            record.sentTime = sent
            DiscordGameNotificationDao(cfg).insertReactive(record)
        }
    }

    suspend fun findGameCreatedNotification(channelId: String, gameId: String): DiscordGameNotification? {
        return dslContext
            .select()
            .from(DISCORD_GAME_NOTIFICATION)
            .where(DISCORD_GAME_NOTIFICATION.GAME_ID.eq(gameId))
            .and(DISCORD_GAME_NOTIFICATION.CHANNEL_ID.eq(channelId))
            .and(DISCORD_GAME_NOTIFICATION.EVENT_TYPE.eq(GAME_CREATED))
            .awaitMappedRecords<DiscordGameNotification>()
            .firstOrNull()
    }

}
