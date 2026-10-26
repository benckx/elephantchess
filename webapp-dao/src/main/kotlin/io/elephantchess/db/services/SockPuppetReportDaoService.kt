package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_SOCK_PUPPET_DETECTION
import io.elephantchess.db.dao.codegen.tables.daos.GameSockPuppetDetectionDao
import io.elephantchess.db.dao.codegen.tables.pojos.GameSockPuppetDetection
import io.elephantchess.db.model.SockPuppetDetectionEntryRecord
import io.elephantchess.db.model.SockPuppetDetectionSummaryRecord
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.db.utils.insertReactive
import io.elephantchess.db.utils.isWithin
import io.elephantchess.utils.di.KoinSingleton
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@KoinSingleton
class SockPuppetReportDaoService(private val dslContext: DSLContext) {

    private val detectionFields =
        arrayOf(
            GAME_SOCK_PUPPET_DETECTION.GAME_ID,
            GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID,
            GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID,
            GAME_SOCK_PUPPET_DETECTION.SHARED_REMOTE_ADDRESS_COUNT,
            GAME_SOCK_PUPPET_DETECTION.SHARED_FINGERPRINT_COUNT,
            GAME_SOCK_PUPPET_DETECTION.SHARED_REMOTE_ADDRESS,
            GAME_SOCK_PUPPET_DETECTION.SHARED_COUNTRY_CODE,
            GAME_SOCK_PUPPET_DETECTION.SHARED_COUNTRY_NAME,
            GAME_SOCK_PUPPET_DETECTION.SHARED_REGION,
            GAME_SOCK_PUPPET_DETECTION.SHARED_CITY,
            GAME_SOCK_PUPPET_DETECTION.SHARED_OPERATING_SYSTEM_NAME,
            GAME_SOCK_PUPPET_DETECTION.SHARED_AGENT_NAME,
            GAME_SOCK_PUPPET_DETECTION.SHARED_AGENT_CLASS,
            GAME_SOCK_PUPPET_DETECTION.SHARED_USER_AGENT,
            GAME.CREATED,
        )

    suspend fun insert(gameSockPuppetDetection: GameSockPuppetDetection) {
        dslContext.transactionCoroutine { cfg ->
            GameSockPuppetDetectionDao(cfg)
                .insertReactive(gameSockPuppetDetection)
        }
    }

    suspend fun findDetectionCreationTimes(gameIds: Collection<String>): Map<String, Instant> {
        if (gameIds.isEmpty()) {
            return emptyMap()
        }

        return dslContext
            .select(GAME_SOCK_PUPPET_DETECTION.GAME_ID, GAME_SOCK_PUPPET_DETECTION.CREATED_AT)
            .from(GAME_SOCK_PUPPET_DETECTION)
            .where(GAME_SOCK_PUPPET_DETECTION.GAME_ID.`in`(gameIds))
            .awaitRecords()
            .mapNotNull { record ->
                val gameId = record.get(GAME_SOCK_PUPPET_DETECTION.GAME_ID)
                val createdAt = record.get(GAME_SOCK_PUPPET_DETECTION.CREATED_AT)
                if (gameId != null && createdAt != null) {
                    gameId to createdAt
                } else {
                    null
                }
            }
            .toMap()
    }

    suspend fun listLatestDetections(limit: Int): List<SockPuppetDetectionEntryRecord> =
        dslContext
            .select(*detectionFields)
            .from(GAME_SOCK_PUPPET_DETECTION)
            .join(GAME)
            .on(GAME.ID.eq(GAME_SOCK_PUPPET_DETECTION.GAME_ID))
            .orderBy(GAME.CREATED.desc())
            .limit(limit)
            .awaitRecords()
            .map(::mapDetectionRecord)

    suspend fun listDetectionsForUser(userId: String): List<SockPuppetDetectionEntryRecord> =
        dslContext
            .select(*detectionFields)
            .from(GAME_SOCK_PUPPET_DETECTION)
            .join(GAME)
            .on(GAME.ID.eq(GAME_SOCK_PUPPET_DETECTION.GAME_ID))
            .where(
                GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID.eq(userId)
                    .or(GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID.eq(userId)),
            )
            .orderBy(GAME.CREATED.desc())
            .awaitRecords()
            .map(::mapDetectionRecord)

    suspend fun fetchDetectionsSummary(cutoff: Duration = 30.days): SockPuppetDetectionSummaryRecord {
        val totalDetections = dslContext
            .selectCount()
            .from(GAME_SOCK_PUPPET_DETECTION)
            .awaitSingleValue() ?: 0

        val allUsersDistinct = dslContext
            .select(GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID.`as`("user_id"))
            .from(GAME_SOCK_PUPPET_DETECTION)
            .union(
                dslContext
                    .select(GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID.`as`("user_id"))
                    .from(GAME_SOCK_PUPPET_DETECTION),
            )
            .asTable("all_users_distinct")

        val distinctUsersInvolved = dslContext
            .selectCount()
            .from(allUsersDistinct)
            .awaitSingleValue() ?: 0

        val allUsers = dslContext
            .select(GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID.`as`("user_id"))
            .from(GAME_SOCK_PUPPET_DETECTION)
            .unionAll(
                dslContext
                    .select(GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID.`as`("user_id"))
                    .from(GAME_SOCK_PUPPET_DETECTION),
            )
            .asTable("all_users")

        val totalUsersInvolved = dslContext
            .selectCount()
            .from(allUsers)
            .awaitSingleValue() ?: 0

        val reportedGamesLast30Days = dslContext
            .selectCount()
            .from(GAME_SOCK_PUPPET_DETECTION)
            .join(GAME)
            .on(GAME.ID.eq(GAME_SOCK_PUPPET_DETECTION.GAME_ID))
            .where(GAME.CREATED.isWithin(cutoff))
            .awaitSingleValue() ?: 0

        val totalPvpGamesLast30Days = dslContext
            .selectCount()
            .from(GAME)
            .where(GAME.CREATED.isWithin(cutoff))
            .and(GAME.INVITEE.isNotNull)
            .awaitSingleValue() ?: 0

        val reportedGamesRatioLast30Days =
            if (totalPvpGamesLast30Days == 0) {
                0.0
            } else {
                reportedGamesLast30Days.toDouble() / totalPvpGamesLast30Days.toDouble()
            }

        return SockPuppetDetectionSummaryRecord(
            totalDetections = totalDetections,
            totalUsersInvolved = totalUsersInvolved,
            distinctUsersInvolved = distinctUsersInvolved,
            reportedGamesLast30Days = reportedGamesLast30Days,
            totalPvpGamesLast30Days = totalPvpGamesLast30Days,
            reportedGamesRatioLast30Days = reportedGamesRatioLast30Days,
        )
    }

    private companion object {

        private fun mapDetectionRecord(record: Record): SockPuppetDetectionEntryRecord =
            SockPuppetDetectionEntryRecord(
                gameId = record.get(GAME_SOCK_PUPPET_DETECTION.GAME_ID),
                inviterUserId = record.get(GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID),
                inviteeUserId = record.get(GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID),
                sharedRemoteAddressCount = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_REMOTE_ADDRESS_COUNT) ?: 0,
                sharedFingerprintCount = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_FINGERPRINT_COUNT) ?: 0,
                sharedRemoteAddress = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_REMOTE_ADDRESS),
                sharedCountryCode = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_COUNTRY_CODE),
                sharedCountryName = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_COUNTRY_NAME),
                sharedRegion = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_REGION),
                sharedCity = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_CITY),
                sharedOperatingSystemName = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_OPERATING_SYSTEM_NAME),
                sharedAgentName = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_AGENT_NAME),
                sharedAgentClass = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_AGENT_CLASS),
                sharedUserAgent = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_USER_AGENT),
                gameCreated = record.get(GAME.CREATED),
            )
    }
}
