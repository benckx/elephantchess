package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.UserSessionDao
import io.elephantchess.db.dao.codegen.tables.pojos.UserSession
import io.elephantchess.db.model.SharedRemoteAddressUserRecord
import io.elephantchess.db.model.SharedSessionSimilarityRecord
import io.elephantchess.db.model.UserSessionRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.UserType
import io.elephantchess.utils.di.KoinSingleton
import io.github.oshai.kotlinlogging.KLogger
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@KoinSingleton
class UserSessionDaoService(private val dslContext: DSLContext, private val logger: KLogger) {

    suspend fun createOrUpdate(record: UserSessionRecord) {
        if (record.userId != null) {
            val userSession = UserSession()
            userSession.userId = record.userId
            userSession.remoteAddress = record.remoteAddress
            userSession.userAgent = record.userAgent
            userSession.operatingSystemName = record.operatingSystemName
            userSession.agentName = record.agentName
            userSession.agentClass = record.agentClass
            createOrUpdate(userSession)
        } else {
            logger.warn { "anonymous user sessions are not supported anymore (creation)" }
        }
    }

    private suspend fun createOrUpdate(userSession: UserSession) {
        dslContext.transactionCoroutine { cfg ->
            val transaction = DSL.using(cfg)
            val now = Clock.System.now()

            val conditions = USER_SESSION.USER_ID.eq(userSession.userId)
                .and(USER_SESSION.REMOTE_ADDRESS.eq(userSession.remoteAddress))
                .and(USER_SESSION.OPERATING_SYSTEM_NAME.eq(userSession.operatingSystemName))
                .and(USER_SESSION.AGENT_NAME.eq(userSession.agentName))
                .and(USER_SESSION.CREATED.isWithin(USER_SESSION_TTL))

            val exists =
                transaction
                    .selectCount()
                    .from(USER_SESSION)
                    .where(conditions)
                    .awaitSingleValue<Int>()!! > 0

            if (exists) {
                // update existing
                transaction
                    .update(USER_SESSION)
                    .set(USER_SESSION.LAST_UPDATED, now)
                    .where(conditions)
                    .awaitExecute()
            } else {
                // create new
                userSession.created = now
                userSession.lastUpdated = now
                UserSessionDao(cfg).insertReactive(userSession)
            }
        }
    }

    suspend fun listAllWithoutGeographicData(minGuestSessionDuration: Duration): List<UserSessionRecord> {
        val records = mutableListOf<UserSessionRecord>()

        // authenticated users
        records += dslContext
            .select()
            .from(USER_SESSION, USER)
            .where(USER_SESSION.COUNTRY_CODE.isNull)
            .and(USER.ID.eq(USER_SESSION.USER_ID))
            .and(USER.USER_TYPE.`in`(listOf(UserType.AUTHENTICATED)))
            .awaitMappedRecords<UserSession>()
            .map { mapToRecord(it) }

        // guests
        records += dslContext
            .select()
            .from(USER_SESSION, USER)
            .where(USER_SESSION.COUNTRY_CODE.isNull)
            .and(USER.ID.eq(USER_SESSION.USER_ID))
            .and(USER.USER_TYPE.`in`(listOf(UserType.GUEST)))
            .and(
                diffInSeconds(USER_SESSION.LAST_UPDATED, USER_SESSION.CREATED)
                    .ge(minGuestSessionDuration.inWholeSeconds.toInt()),
            )
            .awaitMappedRecords<UserSession>()
            .map { mapToRecord(it) }

        return records.toList().sortedByDescending { it.lastUpdated }
    }

    suspend fun listAuthenticatedSessions(limit: Int): List<UserSessionRecord> =
        dslContext
            .select()
            .from(USER_SESSION)
            .orderBy(USER_SESSION.LAST_UPDATED.desc())
            .limit(limit)
            .awaitMappedRecords<UserSession>()
            .map { mapToRecord(it) }

    suspend fun listAuthenticatedSessionsForUser(userId: String, limit: Int, offset: Int = 0): List<UserSessionRecord> =
        dslContext
            .select()
            .from(USER_SESSION)
            .where(USER_SESSION.USER_ID.eq(userId))
            .orderBy(USER_SESSION.LAST_UPDATED.desc())
            .limit(limit)
            .offset(offset)
            .awaitMappedRecords<UserSession>()
            .map { mapToRecord(it) }

    suspend fun countAuthenticatedSessionsForUser(userId: String): Int =
        dslContext
            .selectCount()
            .from(USER_SESSION)
            .where(USER_SESSION.USER_ID.eq(userId))
            .awaitSingleValue() ?: 0

    suspend fun countOtherUsersByRemoteAddresses(userId: String, remoteAddresses: Set<String>): Map<String, Int> {
        if (remoteAddresses.isEmpty()) {
            return emptyMap()
        }

        return dslContext
            .select(
                USER_SESSION.REMOTE_ADDRESS,
                DSL.countDistinct(USER_SESSION.USER_ID).`as`("distinctUsers"),
            )
            .from(USER_SESSION)
            .where(USER_SESSION.REMOTE_ADDRESS.`in`(remoteAddresses))
            .and(USER_SESSION.USER_ID.ne(userId))
            .groupBy(USER_SESSION.REMOTE_ADDRESS)
            .awaitRecords()
            .associate { record ->
                val remoteAddress = record.get(USER_SESSION.REMOTE_ADDRESS)
                val count = record.get("distinctUsers", Int::class.java) ?: 0
                remoteAddress to count
            }
    }

    suspend fun listOtherUsersByRemoteAddresses(
        userId: String,
        remoteAddresses: Set<String>,
        limit: Int,
    ): List<SharedRemoteAddressUserRecord> {
        if (remoteAddresses.isEmpty()) {
            return emptyList()
        }

        val sharedIpCountField = DSL.countDistinct(USER_SESSION.REMOTE_ADDRESS).`as`("sharedIpCount")
        val lastSeenField = DSL.max(USER_SESSION.LAST_UPDATED).`as`("lastSeen")

        return dslContext
            .select(
                USER_SESSION.USER_ID,
                sharedIpCountField,
                lastSeenField,
            )
            .from(USER_SESSION)
            .where(USER_SESSION.REMOTE_ADDRESS.`in`(remoteAddresses))
            .and(USER_SESSION.USER_ID.ne(userId))
            .groupBy(USER_SESSION.USER_ID)
            .orderBy(sharedIpCountField.desc(), lastSeenField.desc())
            .limit(limit)
            .awaitRecords()
            .map { record ->
                SharedRemoteAddressUserRecord(
                    userId = record.get(USER_SESSION.USER_ID),
                    sharedRemoteAddressCount = record.get(sharedIpCountField) ?: 0,
                    lastSeen = record.get(lastSeenField),
                )
            }
    }

    suspend fun countSharedSessionSimilarity(userId1: String, userId2: String): SharedSessionSimilarityRecord {
        val session1 = USER_SESSION.`as`("session1")
        val session2 = USER_SESSION.`as`("session2")

        val sharedRemoteAddressCountField = DSL.countDistinct(session1.REMOTE_ADDRESS).`as`("sharedRemoteAddressCount")
        val fingerprintField = DSL.concat(
            session1.REMOTE_ADDRESS,
            DSL.inline("|"),
            DSL.coalesce(session1.OPERATING_SYSTEM_NAME, DSL.inline("")),
            DSL.inline("|"),
            DSL.coalesce(session1.AGENT_NAME, DSL.inline("")),
        )
        val sharedFingerprintCountField = DSL.countDistinct(fingerprintField).`as`("sharedFingerprintCount")

        val record = dslContext
            .select(sharedRemoteAddressCountField, sharedFingerprintCountField)
            .from(session1)
            .join(session2)
            .on(session1.REMOTE_ADDRESS.eq(session2.REMOTE_ADDRESS))
            .and(session1.OPERATING_SYSTEM_NAME.isNotDistinctFrom(session2.OPERATING_SYSTEM_NAME))
            .and(session1.AGENT_NAME.isNotDistinctFrom(session2.AGENT_NAME))
            .where(session1.USER_ID.eq(userId1))
            .and(session2.USER_ID.eq(userId2))
            .awaitSingleRecord()

        val latestSharedSession = dslContext
            .select(
                session1.REMOTE_ADDRESS,
                session1.COUNTRY_CODE,
                session1.COUNTRY_NAME,
                session1.REGION,
                session1.CITY,
                session1.OPERATING_SYSTEM_NAME,
                session1.AGENT_NAME,
                session1.AGENT_CLASS,
                session1.USER_AGENT,
            )
            .from(session1)
            .join(session2)
            .on(session1.REMOTE_ADDRESS.eq(session2.REMOTE_ADDRESS))
            .and(session1.OPERATING_SYSTEM_NAME.isNotDistinctFrom(session2.OPERATING_SYSTEM_NAME))
            .and(session1.AGENT_NAME.isNotDistinctFrom(session2.AGENT_NAME))
            .where(session1.USER_ID.eq(userId1))
            .and(session2.USER_ID.eq(userId2))
            .orderBy(session1.LAST_UPDATED.desc(), session2.LAST_UPDATED.desc())
            .limit(1)
            .awaitSingleRecord()

        return SharedSessionSimilarityRecord(
            sharedRemoteAddressCount = record?.get(sharedRemoteAddressCountField) ?: 0,
            sharedFingerprintCount = record?.get(sharedFingerprintCountField) ?: 0,
            sharedRemoteAddress = latestSharedSession?.get(session1.REMOTE_ADDRESS),
            sharedCountryCode = latestSharedSession?.get(session1.COUNTRY_CODE),
            sharedCountryName = latestSharedSession?.get(session1.COUNTRY_NAME),
            sharedRegion = latestSharedSession?.get(session1.REGION),
            sharedCity = latestSharedSession?.get(session1.CITY),
            sharedOperatingSystemName = latestSharedSession?.get(session1.OPERATING_SYSTEM_NAME),
            sharedAgentName = latestSharedSession?.get(session1.AGENT_NAME),
            sharedAgentClass = latestSharedSession?.get(session1.AGENT_CLASS),
            sharedUserAgent = latestSharedSession?.get(session1.USER_AGENT),
        )
    }

    suspend fun deleteAuthenticatedSessionsForUser(userId: String, sessionIds: Collection<Int>): Int {
        if (sessionIds.isEmpty()) {
            return 0
        }

        return dslContext
            .deleteFrom(USER_SESSION)
            .where(USER_SESSION.USER_ID.eq(userId))
            .and(USER_SESSION.ID.`in`(sessionIds))
            .awaitExecute()
    }

    suspend fun deleteAllAuthenticatedSessionsForUser(userId: String): Int =
        dslContext
            .deleteFrom(USER_SESSION)
            .where(USER_SESSION.USER_ID.eq(userId))
            .awaitExecute()

    suspend fun findByUserAgent(userAgent: String): List<UserSessionRecord> {
        val records = mutableListOf<UserSessionRecord>()

        records += dslContext
            .select()
            .from(USER_SESSION)
            .where(USER_SESSION.USER_AGENT.eq(userAgent))
            .awaitMappedRecords<UserSession>()
            .map { mapToRecord(it) }

        // this is the only place where we still use anonymous user sessions
        // we could remove the table, but it might still contain mapped user agent data
        records += dslContext
            .select()
            .from(USER_SESSION_ANONYMOUS)
            .where(USER_SESSION_ANONYMOUS.USER_AGENT.eq(userAgent))
            .awaitMappedRecords<UserSession>()
            .map { mapToRecord(it) }

        return records.toList().sortedByDescending { it.lastUpdated }
    }

    suspend fun updateGeographicData(
        userId: String?,
        remoteAddress: String,
        country: String,
        countryCode: String,
        region: String,
        city: String,
    ) {
        if (userId != null) {
            dslContext.transactionCoroutine { cfg ->
                DSL
                    .using(cfg)
                    .update(USER_SESSION)
                    .set(USER_SESSION.COUNTRY_NAME, country)
                    .set(USER_SESSION.COUNTRY_CODE, countryCode)
                    .set(USER_SESSION.REGION, region)
                    .set(USER_SESSION.CITY, city)
                    .where(USER_SESSION.USER_ID.eq(userId))
                    .and(USER_SESSION.REMOTE_ADDRESS.eq(remoteAddress))
                    .awaitExecute()
            }
        } else {
            logger.warn { "anonymous user sessions are not supported anymore (update)" }
        }
    }

    companion object {

        val USER_SESSION_TTL = 60.days

        private fun mapToRecord(userSession: UserSession): UserSessionRecord =
            UserSessionRecord(
                id = userSession.id,
                userId = userSession.userId,
                remoteAddress = userSession.remoteAddress,
                userAgent = userSession.userAgent,
                operatingSystemName = userSession.operatingSystemName,
                agentName = userSession.agentName,
                agentClass = userSession.agentClass,
                created = userSession.created,
                lastUpdated = userSession.lastUpdated,
                countryName = userSession.countryName,
                countryCode = userSession.countryCode,
                region = userSession.region,
                city = userSession.city,
            )
    }
}
