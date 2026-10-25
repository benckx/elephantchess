package io.elephantchess.db.services

import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.utils.di.KoinSingleton
import org.jooq.DSLContext
import org.jooq.impl.DSL
import kotlin.time.Clock

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
                GAME_SOCK_PUPPET_DETECTION_GAME_ID,
                GAME_SOCK_PUPPET_DETECTION_INVITER_USER_ID,
                GAME_SOCK_PUPPET_DETECTION_INVITEE_USER_ID,
                GAME_SOCK_PUPPET_DETECTION_SHARED_REMOTE_ADDRESS_COUNT,
                GAME_SOCK_PUPPET_DETECTION_SHARED_FINGERPRINT_COUNT,
                GAME_SOCK_PUPPET_DETECTION_CREATED_AT,
                GAME_SOCK_PUPPET_DETECTION_LAST_SCANNED_AT,
            )
            .values(
                gameId,
                inviterUserId,
                inviteeUserId,
                sharedRemoteAddressCount,
                sharedFingerprintCount,
                now,
                now,
            )
            .onConflict(GAME_SOCK_PUPPET_DETECTION_GAME_ID)
            .doUpdate()
            .set(GAME_SOCK_PUPPET_DETECTION_SHARED_REMOTE_ADDRESS_COUNT, sharedRemoteAddressCount)
            .set(GAME_SOCK_PUPPET_DETECTION_SHARED_FINGERPRINT_COUNT, sharedFingerprintCount)
            .set(GAME_SOCK_PUPPET_DETECTION_LAST_SCANNED_AT, now)
            .awaitExecute()
    }

    suspend fun findDetectedGameIds(gameIds: Collection<String>): Set<String> {
        if (gameIds.isEmpty()) {
            return emptySet()
        }

        return dslContext
            .select(GAME_SOCK_PUPPET_DETECTION_GAME_ID)
            .from(GAME_SOCK_PUPPET_DETECTION)
            .where(GAME_SOCK_PUPPET_DETECTION_GAME_ID.`in`(gameIds))
            .awaitRecords()
            .map { record -> record.get(GAME_SOCK_PUPPET_DETECTION_GAME_ID) }
            .toSet()
    }

    private companion object {

        val GAME_SOCK_PUPPET_DETECTION = DSL.table(DSL.name("game_sock_puppet_detection"))
        val GAME_SOCK_PUPPET_DETECTION_GAME_ID = DSL.field(DSL.name("game_sock_puppet_detection", "game_id"), String::class.java)
        val GAME_SOCK_PUPPET_DETECTION_INVITER_USER_ID =
            DSL.field(DSL.name("game_sock_puppet_detection", "inviter_user_id"), String::class.java)
        val GAME_SOCK_PUPPET_DETECTION_INVITEE_USER_ID =
            DSL.field(DSL.name("game_sock_puppet_detection", "invitee_user_id"), String::class.java)
        val GAME_SOCK_PUPPET_DETECTION_SHARED_REMOTE_ADDRESS_COUNT = DSL.field(
            DSL.name("game_sock_puppet_detection", "shared_remote_address_count"),
            Int::class.java,
        )
        val GAME_SOCK_PUPPET_DETECTION_SHARED_FINGERPRINT_COUNT = DSL.field(
            DSL.name("game_sock_puppet_detection", "shared_fingerprint_count"),
            Int::class.java,
        )
        val GAME_SOCK_PUPPET_DETECTION_CREATED_AT = DSL.field(
            DSL.name("game_sock_puppet_detection", "created_at"),
            kotlin.time.Instant::class.java,
        )
        val GAME_SOCK_PUPPET_DETECTION_LAST_SCANNED_AT = DSL.field(
            DSL.name("game_sock_puppet_detection", "last_scanned_at"),
            kotlin.time.Instant::class.java,
        )
    }
}
