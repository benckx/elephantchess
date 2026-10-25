package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_SOCK_PUPPET_DETECTION
import io.elephantchess.db.model.SockPuppetDetectionEntryRecord
import io.elephantchess.db.model.SockPuppetDetectionSummaryRecord
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.utils.di.KoinSingleton
import org.jooq.DSLContext
import org.jooq.impl.DSL
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@KoinSingleton
class SockPuppetReportDaoService(private val dslContext: DSLContext) {

    suspend fun upsertGameReport(
        gameId: String,
        inviterUserId: String,
        inviteeUserId: String,
        sharedRemoteAddressCount: Int,
        sharedFingerprintCount: Int,
    ): Int {
        val now = Clock.System.now()

        return dslContext
            .insertInto(
                GAME_SOCK_PUPPET_DETECTION,
                GAME_SOCK_PUPPET_DETECTION.GAME_ID,
                GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID,
                GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID,
                GAME_SOCK_PUPPET_DETECTION.SHARED_REMOTE_ADDRESS_COUNT,
                GAME_SOCK_PUPPET_DETECTION.SHARED_FINGERPRINT_COUNT,
                GAME_SOCK_PUPPET_DETECTION.CREATED_AT,
            )
            .values(
                gameId,
                inviterUserId,
                inviteeUserId,
                sharedRemoteAddressCount,
                sharedFingerprintCount,
                now,
            )
            .onConflict(GAME_SOCK_PUPPET_DETECTION.GAME_ID)
            .doUpdate()
            .set(GAME_SOCK_PUPPET_DETECTION.SHARED_REMOTE_ADDRESS_COUNT, sharedRemoteAddressCount)
            .set(GAME_SOCK_PUPPET_DETECTION.SHARED_FINGERPRINT_COUNT, sharedFingerprintCount)
            .awaitExecute()
    }

    suspend fun findDetectedGameIds(gameIds: Collection<String>): Set<String> {
        if (gameIds.isEmpty()) {
            return emptySet()
        }

        return dslContext
            .select(GAME_SOCK_PUPPET_DETECTION.GAME_ID)
            .from(GAME_SOCK_PUPPET_DETECTION)
            .where(GAME_SOCK_PUPPET_DETECTION.GAME_ID.`in`(gameIds))
            .awaitRecords()
            .map { record -> record.get(GAME_SOCK_PUPPET_DETECTION.GAME_ID) }
            .toSet()
    }

    suspend fun listLatestDetections(limit: Int): List<SockPuppetDetectionEntryRecord> =
        dslContext
            .select(
                GAME_SOCK_PUPPET_DETECTION.GAME_ID,
                GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID,
                GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID,
                GAME_SOCK_PUPPET_DETECTION.SHARED_REMOTE_ADDRESS_COUNT,
                GAME_SOCK_PUPPET_DETECTION.SHARED_FINGERPRINT_COUNT,
                GAME.CREATED,
            )
            .from(GAME_SOCK_PUPPET_DETECTION)
            .join(GAME)
            .on(GAME.ID.eq(GAME_SOCK_PUPPET_DETECTION.GAME_ID))
            .orderBy(GAME.CREATED.desc())
            .limit(limit)
            .awaitRecords()
            .map { record ->
                SockPuppetDetectionEntryRecord(
                    gameId = record.get(GAME_SOCK_PUPPET_DETECTION.GAME_ID),
                    inviterUserId = record.get(GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID),
                    inviteeUserId = record.get(GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID),
                    sharedRemoteAddressCount = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_REMOTE_ADDRESS_COUNT) ?: 0,
                    sharedFingerprintCount = record.get(GAME_SOCK_PUPPET_DETECTION.SHARED_FINGERPRINT_COUNT) ?: 0,
                    gameCreated = record.get(GAME.CREATED),
                )
            }

    suspend fun fetchDetectionsSummary(): SockPuppetDetectionSummaryRecord {
        val last30Days = Clock.System.now().minus(30.days)

        val totalDetections = dslContext
            .selectCount()
            .from(GAME_SOCK_PUPPET_DETECTION)
            .awaitSingleValue() ?: 0

        val distinctInviters = dslContext
            .select(DSL.countDistinct(GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID))
            .from(GAME_SOCK_PUPPET_DETECTION)
            .awaitSingleValue() ?: 0

        val distinctInvitees = dslContext
            .select(DSL.countDistinct(GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID))
            .from(GAME_SOCK_PUPPET_DETECTION)
            .awaitSingleValue() ?: 0

        val allUsers = dslContext
            .select(GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID.`as`("user_id"))
            .from(GAME_SOCK_PUPPET_DETECTION)
            .union(
                dslContext
                    .select(GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID.`as`("user_id"))
                    .from(GAME_SOCK_PUPPET_DETECTION),
            )
            .asTable("all_users")

        val distinctUsersInvolved = dslContext
            .selectCount()
            .from(allUsers)
            .awaitSingleValue() ?: 0

        val reportedGamesLast30Days = dslContext
            .selectCount()
            .from(GAME_SOCK_PUPPET_DETECTION)
            .join(GAME)
            .on(GAME.ID.eq(GAME_SOCK_PUPPET_DETECTION.GAME_ID))
            .where(GAME.CREATED.ge(last30Days))
            .awaitSingleValue() ?: 0

        val totalPvpGamesLast30Days = dslContext
            .selectCount()
            .from(GAME)
            .where(GAME.CREATED.ge(last30Days))
            .and(GAME.INVITEE.isNotNull)
            .awaitSingleValue() ?: 0

        val reportedGamesRatioLast30Days = if (totalPvpGamesLast30Days == 0) {
            0.0
        } else {
            reportedGamesLast30Days.toDouble() / totalPvpGamesLast30Days.toDouble()
        }

        return SockPuppetDetectionSummaryRecord(
            totalDetections = totalDetections,
            distinctUsersInvolved = distinctUsersInvolved,
            distinctInviters = distinctInviters,
            distinctInvitees = distinctInvitees,
            reportedGamesLast30Days = reportedGamesLast30Days,
            totalPvpGamesLast30Days = totalPvpGamesLast30Days,
            reportedGamesRatioLast30Days = reportedGamesRatioLast30Days,
        )
    }
}
