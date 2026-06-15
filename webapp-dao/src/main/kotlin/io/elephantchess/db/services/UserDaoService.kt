package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.UserDao
import io.elephantchess.db.dao.codegen.tables.pojos.User
import io.elephantchess.db.dao.codegen.tables.records.UserRecord
import io.elephantchess.db.model.NotificationsSettingsRecord
import io.elephantchess.db.model.PlayerVsPlayerNumberOfGamesAndLastPlayedRecord
import io.elephantchess.db.model.PuzzleLeaderboardRecord
import io.elephantchess.db.model.UserRatingSummaryRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.TimeControlCategory
import io.elephantchess.model.UserType
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.utils.safeRandomAlphaNumericString
import io.github.oshai.kotlinlogging.KLogger
import io.elephantchess.xiangqi.Variant
import org.jooq.DSLContext
import org.jooq.Record2
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class UserDaoService(private val dslContext: DSLContext, val logger: KLogger) {

    suspend fun save(user: User): String {
        dslContext.transactionCoroutine { cfg ->
            val userDao = UserDao(cfg)
            if (user.id == null) {
                user.id = generateId()
            }
            userDao.insertReactive(user)
        }

        return user.id!!
    }

    suspend fun createGuestUser(): String {
        suspend fun countCollision(transactional: DSLContext, id: String): Int =
            transactional
                .selectCount()
                .from(USER)
                .where(USER.ID.eq(id))
                .and(USER.USER_TYPE.eq(UserType.GUEST))
                .awaitSingleValue()!!

        var id: String? = null

        dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)
            while (id == null || countCollision(transactional, id!!) > 0) {
                id = safeRandomAlphaNumericString(GUEST_ID_MIN, GUEST_ID_MAX + 1)
            }

            val now = Clock.System.now()

            transactional
                .insertInto(USER)
                .set(USER.ID, id)
                .set(USER.CREATION, now)
                .set(USER.LAST_ONLINE, now)
                .set(USER.USER_TYPE, UserType.GUEST)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_NEWSLETTER, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_USER_JOINED_GAME, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PLAYED_MOVE, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_RESIGNED, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PROPOSED_DRAW, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_ACCEPTED_DRAW, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_DECLINED_DRAW, false)
                .set(USER.PUZZLE_RATING, 800)
                .awaitExecute()
        }

        return id!!
    }

    suspend fun fetchProfileSettings(userId: String): Record2<String, String>? {
        return dslContext
            .select(
                USER.DESCRIPTION,
                USER.COUNTRY
            )
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitRecords()
            .firstOrNull()
    }

    suspend fun updateProfileSettings(userId: String, description: String, country: String?) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER.fixed())
                .set(USER.DESCRIPTION.fixed(), description)
                .set(USER.COUNTRY.fixed(), country)
                .set(USER.LAST_PROFILE_UPDATE.fixed(), Clock.System.now())
                .where(USER.ID.fixed().eq(userId))
                .awaitExecute()
        }
    }

    suspend fun fetchNotificationSettings(userId: String): NotificationsSettingsRecord? {
        return dslContext
            .select(
                USER.EMAIL_NOTIFICATION_ENABLED_NEWSLETTER,
                USER.EMAIL_NOTIFICATION_ENABLED_USER_JOINED_GAME,
                USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PLAYED_MOVE,
                USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_RESIGNED,
                USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PROPOSED_DRAW,
                USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_ACCEPTED_DRAW,
                USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_DECLINED_DRAW
            )
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleMappedRecord<User>()
            ?.let { userRecord ->
                NotificationsSettingsRecord(
                    newsletter = userRecord.emailNotificationEnabledNewsletter,
                    opponentJoinedGame = userRecord.emailNotificationEnabledUserJoinedGame,
                    opponentPlayedMove = userRecord.emailNotificationEnabledOpponentPlayedMove,
                    opponentResigned = userRecord.emailNotificationEnabledOpponentResigned,
                    opponentProposedDraw = userRecord.emailNotificationEnabledOpponentProposedDraw,
                    opponentAcceptedDraw = userRecord.emailNotificationEnabledOpponentAcceptedDraw,
                    opponentDeclinedDraw = userRecord.emailNotificationEnabledOpponentDeclinedDraw,
                )
            }
    }

    suspend fun updateNotificationSettings(
        userId: String,
        newsletter: Boolean,
        opponentJoinedGame: Boolean,
        opponentPlayedMove: Boolean,
        opponentResigned: Boolean,
        opponentProposedDraw: Boolean,
        opponentAcceptedDraw: Boolean,
        opponentDeclinedDraw: Boolean,
    ) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER.fixed())
                .set(USER.EMAIL_NOTIFICATION_ENABLED_NEWSLETTER.fixed(), newsletter)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_USER_JOINED_GAME.fixed(), opponentJoinedGame)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PLAYED_MOVE.fixed(), opponentPlayedMove)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_RESIGNED.fixed(), opponentResigned)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PROPOSED_DRAW.fixed(), opponentProposedDraw)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_ACCEPTED_DRAW.fixed(), opponentAcceptedDraw)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_DECLINED_DRAW.fixed(), opponentDeclinedDraw)
                .where(USER.ID.eq(userId))
                .awaitExecute()
        }
    }

    suspend fun unsubscribeFromNewsletter(emailAddress: String) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER.fixed())
                .set(USER.EMAIL_NOTIFICATION_ENABLED_NEWSLETTER.fixed(), false)
                .where(USER.EMAIL.eqIgnoreCaseTrimmed(emailAddress))
                .awaitExecute()
        }
    }

    suspend fun unsubscribeFromAllEmailNotifications(emailAddress: String) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER.fixed())
                .set(USER.EMAIL_NOTIFICATION_ENABLED_NEWSLETTER.fixed(), false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_USER_JOINED_GAME.fixed(), false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PLAYED_MOVE.fixed(), false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_RESIGNED.fixed(), false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PROPOSED_DRAW.fixed(), false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_ACCEPTED_DRAW.fixed(), false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_DECLINED_DRAW.fixed(), false)
                .where(USER.EMAIL.eqIgnoreCaseTrimmed(emailAddress))
                .awaitExecute()
        }
    }

    suspend fun fetchEmail(userId: String): String? {
        return dslContext
            .select(USER.EMAIL)
            .from(USER)
            .where(USER.ID.eq(userId))
            .and(USER.USER_TYPE.eq(AUTHENTICATED))
            .awaitSingleValue()
    }

    suspend fun listNewsletterEmailAddresses(): List<String> {
        return dslContext
            .select(USER.EMAIL)
            .from(USER)
            .where(USER.EMAIL.isNotNull)
            .and(USER.EMAIL_NOTIFICATION_ENABLED_NEWSLETTER.isTrue)
            .awaitMappedRecords()
    }

    suspend fun updatePassword(userId: String, password: ByteArray) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER.fixed())
                .set(USER.PASSWORD.fixed(), password)
                .where(USER.ID.fixed().eq(userId))
                .awaitExecute()
        }
    }

    /**
     * Case-insensitive email, case sensitive username
     */
    suspend fun findByLogin(login: String): User? {
        return dslContext
            .select()
            .from(USER)
            .where(USER.HANDLE.eq(login))
            .or(DSL.lower(USER.EMAIL).eq(login.lowercase()))
            .awaitSingleMappedRecord()
    }

    suspend fun findByUserName(username: String): User? {
        return dslContext
            .select()
            .from(USER)
            .where(USER.HANDLE.eq(username))
            .awaitSingleMappedRecord()
    }

    /**
     * Case-insensitive and trim-insensitive
     */
    suspend fun findByEmail(email: String): User? {
        return dslContext
            .select()
            .from(USER)
            .where(USER.EMAIL.eqIgnoreCaseTrimmed(email))
            .awaitSingleMappedRecord()
    }

    suspend fun findUserNameById(userId: String): String? {
        return dslContext
            .select(USER.HANDLE)
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleValue()
    }

    suspend fun searchForUsernames(usernames: List<String>): List<String> {
        return dslContext
            .select(USER.ID)
            .from(USER)
            .where(
                DSL.lower(DSL.trim(USER.HANDLE))
                    .`in`(usernames.map { it.trim().lowercase() })
            )
            .awaitMappedRecords()
    }

    suspend fun fetchBasicInformation(userId: String): User? {
        return dslContext
            .select(
                USER.ID,
                USER.HANDLE,
                USER.USER_TYPE,
                USER.CREATION,
                USER.HAS_ROLE_ADMIN,
                USER.HAS_ROLE_EDITOR,
            )
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleOrNull()
    }

    suspend fun fetchDescriptionByUsername(username: String): String? {
        return dslContext
            .select(USER.DESCRIPTION)
            .from(USER)
            .where(USER.HANDLE.eq(username))
            .awaitSingleValue()
    }

    suspend fun fetchLastOnline(userIds: List<String>): Map<String, Instant> {
        return dslContext
            .select(USER.ID, USER.LAST_ONLINE)
            .from(USER)
            .where(USER.ID.`in`(userIds))
            .awaitRecords()
            .associate { record ->
                record.get(USER.ID) to record.get(USER.LAST_ONLINE)
            }
    }

    suspend fun findById(userId: String): User? {
        return dslContext
            .select()
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleMappedRecord()
    }

    suspend fun listManuallyConfirmedEmailAddresses(): List<String> {
        return dslContext
            .select(USER.EMAIL)
            .from(USER)
            .where(USER.EMAIL.isNotNull)
            .and(USER.EMAIL_CONFIRMED_AT.isNotNull)
            .awaitMappedRecords()
    }

    suspend fun findByEmailConfirmationCode(code: String): User? {
        return dslContext
            .select()
            .from(USER)
            .where(USER.EMAIL_CONFIRMATION_CODE.eq(code))
            .awaitSingleMappedRecord()
    }

    suspend fun markEmailConfirmed(userId: String, confirmedAt: Instant) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER)
                .set(USER.EMAIL_CONFIRMED_AT.fixed(), confirmedAt)
                .where(USER.ID.eq(userId))
                .awaitExecute()
        }
    }

    suspend fun updateEmailConfirmationCode(userId: String, code: String, createdAt: Instant) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER)
                .set(USER.EMAIL_CONFIRMATION_CODE, code)
                .set(USER.EMAIL_CONFIRMATION_CODE_CREATED_AT.fixed(), createdAt)
                .where(USER.ID.eq(userId))
                .awaitExecute()
        }
    }

    suspend fun existsById(userId: String): Boolean {
        return dslContext
            .selectCount()
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleValue<Int>()!! > 0
    }

    /**
     * Case-insensitive and trim-insensitive
     */
    suspend fun existsForEmail(email: String): Boolean {
        return dslContext
            .selectCount()
            .from(USER)
            .where(USER.EMAIL.eqIgnoreCaseTrimmed(email))
            .awaitSingleValue<Int>()!! > 0
    }

    suspend fun existsForUsername(username: String): Boolean {
        return dslContext
            .selectCount()
            .from(USER)
            .where(USER.HANDLE.eq(username))
            .awaitSingleValue<Int>()!! > 0
    }

    suspend fun updateLastOnline(userId: String) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER)
                .set(USER.LAST_ONLINE.fixed(), Clock.System.now())
                .where(USER.ID.eq(userId))
                .awaitExecute()
        }
    }

    suspend fun transferRatingsFromGuest(guestUserId: String, newUserId: String) {
        val guestUser = findById(guestUserId)
        if (guestUser == null) {
            logger.warn { "transferRatingsFromGuest: guest user $guestUserId not found — ratings not transferred to $newUserId" }
            return
        }
        dslContext.transactionCoroutine { cfg ->
            DSL.using(cfg)
                .update(USER)
                .set(USER.PUZZLE_RATING, guestUser.puzzleRating)
                .set(USER.GAME_RATING_BULLET, guestUser.gameRatingBullet)
                .set(USER.GAME_RATING_BLITZ, guestUser.gameRatingBlitz)
                .set(USER.GAME_RATING_RAPID, guestUser.gameRatingRapid)
                .set(USER.GAME_RATING_CLASSICAL, guestUser.gameRatingClassical)
                .set(USER.GAME_RATING_SEVERAL_DAYS, guestUser.gameRatingSeveralDays)
                .set(USER.GAME_RATING_CORRESPONDENCE, guestUser.gameRatingCorrespondence)
                .set(USER.GAME_RATING_MANCHU_BULLET, guestUser.gameRatingManchuBullet)
                .set(USER.GAME_RATING_MANCHU_BLITZ, guestUser.gameRatingManchuBlitz)
                .set(USER.GAME_RATING_MANCHU_RAPID, guestUser.gameRatingManchuRapid)
                .set(USER.GAME_RATING_MANCHU_CLASSICAL, guestUser.gameRatingManchuClassical)
                .set(USER.GAME_RATING_MANCHU_SEVERAL_DAYS, guestUser.gameRatingManchuSeveralDays)
                .set(USER.GAME_RATING_MANCHU_CORRESPONDENCE, guestUser.gameRatingManchuCorrespondence)
                .where(USER.ID.eq(newUserId))
                .awaitExecute()
        }
    }

    suspend fun updateLastOnline(userIds: List<String>) {
        if (userIds.isNotEmpty()) {
            dslContext.transactionCoroutine { cfg ->
                DSL
                    .using(cfg)
                    .update(USER)
                    .set(USER.LAST_ONLINE.fixed(), Clock.System.now())
                    .where(USER.ID.`in`(userIds))
                    .awaitExecute()
            }
        }
    }

    suspend fun updateSessionExpirationInfo(
        userId: String,
        oldExpirationTime: Instant,
        newExpirationTime: Instant,
    ) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER)
                .set(USER.SESSION_PROLONGED_AT.fixed(), Clock.System.now())
                .set(USER.OLD_EXPIRATION_TIME.fixed(), oldExpirationTime)
                .set(USER.NEW_EXPIRATION_TIME.fixed(), newExpirationTime)
                .where(USER.ID.fixed().eq(userId))
                .awaitExecute()
        }
    }

    suspend fun fetchPuzzleLeaderboard(maxNumberOfEntries: Int): List<PuzzleLeaderboardRecord> {
        return fetchPuzzleLeaderboard(maxNumberOfEntries, Instant.fromEpochSeconds(0L))
    }

    suspend fun fetchPuzzleLeaderboard(maxNumberOfEntries: Int, days: Long): List<PuzzleLeaderboardRecord> {
        val since = Clock.System.now().minusDays(days)
        return fetchPuzzleLeaderboard(maxNumberOfEntries, since)
    }

    suspend fun fetchPuzzleLeaderboard(maxNumberOfEntries: Int, since: Instant): List<PuzzleLeaderboardRecord> {
        return dslContext
            .select(
                USER.ID,
                USER.HANDLE,
                USER.COUNTRY,
                USER.PUZZLE_RATING,
                DSL.max(PUZZLE_RESULT.PLAYER_RATING_TO).`as`("max_rating"),
                DSL.count().`as`("total_played"),
                DSL.max(PUZZLE_RESULT.ENTRY_CREATION).`as`("last_played")
            )
            .from(USER, PUZZLE_RESULT)
            .where(USER.ID.eq(PUZZLE_RESULT.USER_ID))
            .and(USER.USER_TYPE.eq(AUTHENTICATED))
            .and(PUZZLE_RESULT.ENTRY_CREATION.ge(since))
            .groupBy(USER.ID, USER.HANDLE, USER.PUZZLE_RATING)
            .orderBy(USER.PUZZLE_RATING.desc())
            .limit(maxNumberOfEntries)
            .awaitRecords()
            .map { record ->
                PuzzleLeaderboardRecord(
                    userId = record.get(USER.ID),
                    username = record.get(USER.HANDLE),
                    countryCode = record.get(USER.COUNTRY),
                    currentRating = record.get(USER.PUZZLE_RATING),
                    maxRating = record.get("max_rating", Int::class.java),
                    totalPlayed = record.get("total_played", Int::class.java),
                    lastPlayed = record.get("last_played", Instant::class.java)
                )
            }
    }

    suspend fun fetchAllRatings(userId: String): User? {
        return dslContext
            .select(listRatingFields())
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleMappedRecord()
    }

    suspend fun fetchNumberOfGamesAndLastPlayed(userIds: List<String>): List<PlayerVsPlayerNumberOfGamesAndLastPlayedRecord> {
        return TimeControlCategory
            .entries
            .filterNot { it == TimeControlCategory.SEVERAL_DAYS }
            .flatMap { timeControlCategory ->
                dslContext
                    .select(
                        USER.ID,
                        GAME.TIME_CONTROL_CATEGORY,
                        DSL.max(GAME.LAST_UPDATED).`as`("last_played"),
                        DSL.count(GAME.ID).`as`("total_played")
                    )
                    .from(USER, GAME)
                    .where(USER.USER_TYPE.eq(AUTHENTICATED))
                    .and(USER.ID.`in`(userIds))
                    .and(GAME.INVITEE.eq(USER.ID).or(GAME.INVITER.eq(USER.ID)))
                    .and(GAME.TIME_CONTROL_CATEGORY.eq(timeControlCategory))
                    .groupBy(USER.ID, GAME.TIME_CONTROL_CATEGORY)
                    .awaitRecords()
                    .map { record4 ->
                        PlayerVsPlayerNumberOfGamesAndLastPlayedRecord(
                            userId = record4.get(USER.ID),
                            category = record4.get(GAME.TIME_CONTROL_CATEGORY),
                            totalPlayed = record4.get("total_played", Int::class.java),
                            lastPlayed = record4.get("last_played", Instant::class.java)
                        )
                    }
            }
    }

    suspend fun fetchUsersWithHighestRating(numberOfUsers: Int): Map<TimeControlCategory, List<User>> {
        return listRatingFields().mapNotNull { field ->
            mapRatingFieldToTimeControlCategory(field)?.let { category ->
                val users = dslContext
                    .select()
                    .from(USER)
                    .where(field.isNotNull)
                    .and(USER.USER_TYPE.eq(AUTHENTICATED))
                    .orderBy(field.desc())
                    .limit(numberOfUsers)
                    .awaitMappedRecords<User>()

                category to users
            }
        }
            .toMap()
    }

    suspend fun fetchRatingSummary(
        timeControlCategory: TimeControlCategory,
        variant: Variant,
    ): UserRatingSummaryRecord {
        val ratingField = findRatingField(timeControlCategory, variant)
        val aggregateRecord =
            dslContext
                .select(
                    DSL.count().`as`("user_count"),
                    DSL.avg(ratingField).`as`("avg_rating")
                )
                .from(USER)
                .where(USER.USER_TYPE.eq(AUTHENTICATED))
                .awaitSingleRecord()

        suspend fun mapExtremumUser(ascending: Boolean): Triple<String, String, Int>? {
            val sortField = if (ascending) ratingField.asc() else ratingField.desc()
            val record =
                dslContext
                    .select(USER.ID, USER.HANDLE, ratingField.`as`("rating"))
                    .from(USER)
                    .where(USER.USER_TYPE.eq(AUTHENTICATED))
                    .and(ratingField.isNotNull)
                    .orderBy(sortField, USER.HANDLE.asc())
                    .limit(1)
                    .awaitSingleRecord()
                    ?: return null

            return Triple(
                record.get(USER.ID),
                record.get(USER.HANDLE),
                record.get("rating", Int::class.java)
            )
        }

        val minUser = mapExtremumUser(ascending = true)
        val maxUser = mapExtremumUser(ascending = false)

        return UserRatingSummaryRecord(
            variant = variant,
            timeControlCategory = timeControlCategory,
            userCount = aggregateRecord?.get("user_count", Int::class.java) ?: 0,
            averageRating = aggregateRecord?.get("avg_rating", BigDecimal::class.java)?.toDouble(),
            minUserId = minUser?.first,
            minUsername = minUser?.second,
            minRating = minUser?.third,
            maxUserId = maxUser?.first,
            maxUsername = maxUser?.second,
            maxRating = maxUser?.third,
        )
    }

    suspend fun countAuthenticated(): Int {
        return dslContext
            .selectCount()
            .from(USER)
            .where(USER.USER_TYPE.eq(AUTHENTICATED))
            .awaitSingleValue()!!
    }

    suspend fun countGuestsWithSessionAtLeast(sessionDuration: Duration): Int {
        return dslContext
            .selectCount()
            .from(USER)
            .where(USER.USER_TYPE.eq(UserType.GUEST))
            .and(diffInSeconds(USER.LAST_ONLINE, USER.CREATION).gt(sessionDuration.inWholeSeconds.toInt()))
            .awaitSingleValue()!!
    }

    suspend fun countGuestsWithSessionAtLeastAndActiveWithin(sessionDuration: Duration, activeWithin: Duration): Int {
        return dslContext
            .selectCount()
            .from(USER)
            .where(USER.USER_TYPE.eq(UserType.GUEST))
            .and(diffInSeconds(USER.LAST_ONLINE, USER.CREATION).gt(sessionDuration.inWholeSeconds.toInt()))
            .and(USER.LAST_ONLINE.isWithin(activeWithin))
            .awaitSingleValue()!!
    }

    suspend fun countActiveRecently(
        duration: Duration,
        userTypes: List<UserType>,
        excludeIds: List<String> = emptyList(),
    ): Int {
        var select =
            dslContext
                .selectCount()
                .from(USER)
                .where(USER.LAST_ONLINE.isWithin(duration))

        if (excludeIds.isNotEmpty()) {
            select = select.and(USER.ID.notIn(excludeIds))
        }

        if (userTypes.isNotEmpty()) {
            select = select.and(USER.USER_TYPE.`in`(userTypes))
        }

        return select.awaitSingleValue()!!
    }

    suspend fun listRecentlyActiveMinutes(minutes: Int) = listRecentlyActive(minutes.minutes)

    suspend fun listRecentlyActiveSeconds(seconds: Int) = listRecentlyActive(seconds.seconds)

    private suspend fun listRecentlyActive(duration: Duration): List<User> {
        return dslContext
            .select(
                USER.ID,
                USER.HANDLE,
                USER.USER_TYPE
            )
            .from(USER)
            .where(USER.LAST_ONLINE.isWithin(duration))
            .awaitMappedRecords<User>()
    }

    private companion object {

        const val GUEST_ID_MIN = 3
        const val GUEST_ID_MAX = 8

        fun listRatingFields(): List<TableField<UserRecord, Int>> {
            val fields = mutableListOf<TableField<UserRecord, Int>>()
            fields += USER.GAME_RATING_BULLET
            fields += USER.GAME_RATING_BLITZ
            fields += USER.GAME_RATING_RAPID
            fields += USER.GAME_RATING_CLASSICAL
            fields += USER.GAME_RATING_SEVERAL_DAYS
            fields += USER.GAME_RATING_CORRESPONDENCE
            fields += USER.GAME_RATING_MANCHU_BULLET
            fields += USER.GAME_RATING_MANCHU_BLITZ
            fields += USER.GAME_RATING_MANCHU_RAPID
            fields += USER.GAME_RATING_MANCHU_CLASSICAL
            fields += USER.GAME_RATING_MANCHU_SEVERAL_DAYS
            fields += USER.GAME_RATING_MANCHU_CORRESPONDENCE
            return fields
        }

        fun mapRatingFieldToTimeControlCategory(field: TableField<UserRecord, Int>): TimeControlCategory? {
            return when (field) {
                USER.GAME_RATING_BULLET -> TimeControlCategory.BULLET
                USER.GAME_RATING_BLITZ -> TimeControlCategory.BLITZ
                USER.GAME_RATING_RAPID -> TimeControlCategory.RAPID
                USER.GAME_RATING_CLASSICAL -> TimeControlCategory.CLASSICAL
                USER.GAME_RATING_SEVERAL_DAYS -> TimeControlCategory.SEVERAL_DAYS
                USER.GAME_RATING_CORRESPONDENCE -> TimeControlCategory.CORRESPONDENCE
                else -> null
            }
        }

        fun findRatingField(
            timeControlCategory: TimeControlCategory,
            variant: Variant,
        ): TableField<UserRecord, Int> {
            return when (variant) {
                Variant.XIANGQI -> when (timeControlCategory) {
                    TimeControlCategory.BULLET -> USER.GAME_RATING_BULLET
                    TimeControlCategory.BLITZ -> USER.GAME_RATING_BLITZ
                    TimeControlCategory.RAPID -> USER.GAME_RATING_RAPID
                    TimeControlCategory.CLASSICAL -> USER.GAME_RATING_CLASSICAL
                    TimeControlCategory.SEVERAL_DAYS -> USER.GAME_RATING_SEVERAL_DAYS
                    TimeControlCategory.CORRESPONDENCE -> USER.GAME_RATING_CORRESPONDENCE
                }

                Variant.MANCHU -> when (timeControlCategory) {
                    TimeControlCategory.BULLET -> USER.GAME_RATING_MANCHU_BULLET
                    TimeControlCategory.BLITZ -> USER.GAME_RATING_MANCHU_BLITZ
                    TimeControlCategory.RAPID -> USER.GAME_RATING_MANCHU_RAPID
                    TimeControlCategory.CLASSICAL -> USER.GAME_RATING_MANCHU_CLASSICAL
                    TimeControlCategory.SEVERAL_DAYS -> USER.GAME_RATING_MANCHU_SEVERAL_DAYS
                    TimeControlCategory.CORRESPONDENCE -> USER.GAME_RATING_MANCHU_CORRESPONDENCE
                }
            }
        }

    }

    suspend fun latestNewGuestUser(): Instant? {
        return dslContext
            .select(USER.CREATION)
            .from(USER)
            .where(USER.USER_TYPE.eq(UserType.GUEST))
            .orderBy(USER.CREATION.desc())
            .limit(1)
            .awaitSingleValue()
    }

    suspend fun latestNewAuthenticatedUser(): Instant? {
        return dslContext
            .select(USER.CREATION)
            .from(USER)
            .where(USER.USER_TYPE.eq(AUTHENTICATED))
            .orderBy(USER.CREATION.desc())
            .limit(1)
            .awaitSingleValue()
    }

    suspend fun listUsersWithDescriptionForSiteMap(): List<Pair<String, Instant>> {
        return dslContext
            .select(USER.HANDLE, USER.LAST_PROFILE_UPDATE)
            .from(USER)
            .where(USER.DESCRIPTION.isNotNull)
            .and(USER.DESCRIPTION.ne(""))
            .and(USER.LAST_PROFILE_UPDATE.isNotNull)
            .orderBy(USER.HANDLE.asc())
            .awaitRecords()
            .map { record ->
                record.get(USER.HANDLE) to record.get(USER.LAST_PROFILE_UPDATE)
            }
    }

}
