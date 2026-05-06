package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.UserSessionDao
import io.elephantchess.db.dao.codegen.tables.pojos.UserSession
import io.elephantchess.db.model.UserSessionRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.UserType
import io.github.oshai.kotlinlogging.KLogger
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class UserSessionDaoService(
    private val dslContext: DSLContext,
    private val logger: KLogger,
) {

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
                    .set(USER_SESSION.LAST_UPDATED.fixed(), now)
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
                    .ge(minGuestSessionDuration.inWholeSeconds.toInt())
            )
            .awaitMappedRecords<UserSession>()
            .map { mapToRecord(it) }

        return records.toList().sortedByDescending { it.lastUpdated }
    }

    suspend fun listAuthenticatedSessions(limit: Int): List<UserSessionRecord> {
        return dslContext
            .select()
            .from(USER_SESSION)
            .orderBy(USER_SESSION.LAST_UPDATED.desc())
            .limit(limit)
            .awaitMappedRecords<UserSession>()
            .map { mapToRecord(it) }
    }

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
                    .set(USER_SESSION.COUNTRY_NAME.fixed(), country)
                    .set(USER_SESSION.COUNTRY_CODE.fixed(), countryCode)
                    .set(USER_SESSION.REGION.fixed(), region)
                    .set(USER_SESSION.CITY.fixed(), city)
                    .where(USER_SESSION.USER_ID.eq(userId))
                    .and(USER_SESSION.REMOTE_ADDRESS.eq(remoteAddress))
                    .awaitExecute()
            }
        } else {
            logger.warn { "anonymous user sessions are not supported anymore (update)" }
        }
    }

    private companion object {

        val USER_SESSION_TTL = 60.days

        fun mapToRecord(userSession: UserSession): UserSessionRecord {
            return UserSessionRecord(
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
                city = userSession.city
            )
        }

    }

}
