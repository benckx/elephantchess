package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.GAME_CHAT_MESSAGE
import io.elephantchess.db.dao.codegen.tables.daos.GameChatMessageDao
import io.elephantchess.db.dao.codegen.tables.pojos.GameChatMessage
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.db.utils.insertReactive
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock

class ChatMessageDaoService(private val dslContext: DSLContext) {

    suspend fun insertChat(gameId: String, userId: String, content: String) {
        dslContext.transactionCoroutine { cfg ->
            val index = currentIndex(gameId, cfg.dsl())
            val record = GameChatMessage()
            record.gameId = gameId
            record.index = index
            record.author = userId
            record.messageTime = Clock.System.now()
            record.content = content
            GameChatMessageDao(cfg).insertReactive(record)
        }
    }

    suspend fun currentIndex(gameId: String): Int {
        return currentIndex(gameId, dslContext)
    }

    private suspend fun currentIndex(gameId: String, context: DSLContext): Int {
        val max = context
            .select(DSL.max(GAME_CHAT_MESSAGE.INDEX))
            .from(GAME_CHAT_MESSAGE)
            .where(GAME_CHAT_MESSAGE.GAME_ID.eq(gameId))
            .awaitSingleValue<Int>()

        return if (max == null) {
            0
        } else {
            max + 1
        }
    }

    suspend fun currentIndexes(gameIds: List<String>): Map<String, Int> {
        val maxIndexes = dslContext
            .select(
                GAME_CHAT_MESSAGE.GAME_ID,
                DSL.max(GAME_CHAT_MESSAGE.INDEX).`as`("currentIndex")
            )
            .from(GAME_CHAT_MESSAGE)
            .where(GAME_CHAT_MESSAGE.GAME_ID.`in`(gameIds))
            .groupBy(GAME_CHAT_MESSAGE.GAME_ID)
            .awaitRecords()
            .map { record ->
                record[GAME_CHAT_MESSAGE.GAME_ID] to (record.get("currentIndex", Int::class.java))
            }

        return gameIds.associate { gameId ->
            val max = maxIndexes.find { it.first == gameId }?.second
            if (max != null) {
                gameId to max + 1
            } else {
                gameId to 0
            }
        }
    }

    suspend fun listMessagesAfterOrEqualToIndex(gameId: String, index: Int): List<GameChatMessage> {
        return dslContext
            .selectFrom(GAME_CHAT_MESSAGE)
            .where(GAME_CHAT_MESSAGE.GAME_ID.eq(gameId))
            .and(GAME_CHAT_MESSAGE.INDEX.ge(index))
            .orderBy(GAME_CHAT_MESSAGE.INDEX.asc())
            .awaitMappedRecords()
    }

    suspend fun listAllMessages(gameId: String): List<GameChatMessage> {
        return dslContext
            .selectFrom(GAME_CHAT_MESSAGE)
            .where(GAME_CHAT_MESSAGE.GAME_ID.eq(gameId))
            .orderBy(GAME_CHAT_MESSAGE.INDEX.asc())
            .awaitMappedRecords()
    }

    suspend fun listLastMessages(limit: Int): List<GameChatMessage> {
        return dslContext
            .selectFrom(GAME_CHAT_MESSAGE)
            .orderBy(GAME_CHAT_MESSAGE.MESSAGE_TIME.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    suspend fun countMessages(gameIds: List<String>): Map<String, Int> {
        val conditions = gameIds.map { gameId ->
            GAME_CHAT_MESSAGE.GAME_ID.eq(gameId)
        }

        if (conditions.isEmpty()) {
            return emptyMap()
        }

        val orCombinedCondition = conditions.reduce { acc, condition -> acc.or(condition) }

        return dslContext
            .select(GAME_CHAT_MESSAGE.GAME_ID, DSL.count())
            .from(GAME_CHAT_MESSAGE)
            .where(orCombinedCondition)
            .groupBy(GAME_CHAT_MESSAGE.GAME_ID)
            .awaitRecords()
            .associate { record2 ->
                record2.value1() to record2.value2() as Int
            }
    }

}
