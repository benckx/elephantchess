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
                SOCK_PUPPET_REPORT,
                SOCK_PUPPET_REPORT_GAME_ID,
                SOCK_PUPPET_REPORT_INVITER_USER_ID,
                SOCK_PUPPET_REPORT_INVITEE_USER_ID,
                SOCK_PUPPET_REPORT_SHARED_REMOTE_ADDRESS_COUNT,
                SOCK_PUPPET_REPORT_SHARED_FINGERPRINT_COUNT,
                SOCK_PUPPET_REPORT_CREATED_AT,
                SOCK_PUPPET_REPORT_LAST_SCANNED_AT,
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
            .onConflict(SOCK_PUPPET_REPORT_GAME_ID)
            .doUpdate()
            .set(SOCK_PUPPET_REPORT_SHARED_REMOTE_ADDRESS_COUNT, sharedRemoteAddressCount)
            .set(SOCK_PUPPET_REPORT_SHARED_FINGERPRINT_COUNT, sharedFingerprintCount)
            .set(SOCK_PUPPET_REPORT_LAST_SCANNED_AT, now)
            .awaitExecute()
    }

    private companion object {

        val SOCK_PUPPET_REPORT = DSL.table(DSL.name("sock_puppet_report"))
        val SOCK_PUPPET_REPORT_GAME_ID = DSL.field(DSL.name("sock_puppet_report", "game_id"), String::class.java)
        val SOCK_PUPPET_REPORT_INVITER_USER_ID = DSL.field(DSL.name("sock_puppet_report", "inviter_user_id"), String::class.java)
        val SOCK_PUPPET_REPORT_INVITEE_USER_ID = DSL.field(DSL.name("sock_puppet_report", "invitee_user_id"), String::class.java)
        val SOCK_PUPPET_REPORT_SHARED_REMOTE_ADDRESS_COUNT = DSL.field(
            DSL.name("sock_puppet_report", "shared_remote_address_count"),
            Int::class.java,
        )
        val SOCK_PUPPET_REPORT_SHARED_FINGERPRINT_COUNT = DSL.field(
            DSL.name("sock_puppet_report", "shared_fingerprint_count"),
            Int::class.java,
        )
        val SOCK_PUPPET_REPORT_CREATED_AT = DSL.field(DSL.name("sock_puppet_report", "created_at"), kotlin.time.Instant::class.java)
        val SOCK_PUPPET_REPORT_LAST_SCANNED_AT = DSL.field(
            DSL.name("sock_puppet_report", "last_scanned_at"),
            kotlin.time.Instant::class.java,
        )
    }
}
