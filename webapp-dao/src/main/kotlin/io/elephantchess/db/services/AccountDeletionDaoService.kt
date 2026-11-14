package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.ANALYSIS
import io.elephantchess.db.dao.codegen.Tables.BOT_GAME
import io.elephantchess.db.dao.codegen.Tables.DISCORD_GAME_NOTIFICATION
import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_CHAT_MESSAGE
import io.elephantchess.db.dao.codegen.Tables.GAME_SOCK_PUPPET_DETECTION
import io.elephantchess.db.dao.codegen.Tables.GAME_STATUS_EVENT
import io.elephantchess.db.dao.codegen.Tables.KOFI_EVENT
import io.elephantchess.db.dao.codegen.Tables.PAGE_VIEW_EVENT
import io.elephantchess.db.dao.codegen.Tables.PUZZLE_RESULT
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME_SEARCH_QUERY
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_PLAYER_PROFILE_EDIT
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_PLAYER_PROFILE_EDIT_SOURCE
import io.elephantchess.db.dao.codegen.Tables.SEVEN_KINGDOMS_GAME
import io.elephantchess.db.dao.codegen.Tables.SEVEN_KINGDOMS_GAME_EVENT
import io.elephantchess.db.dao.codegen.Tables.UPCOMING_EVENT
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.dao.codegen.Tables.USER_SESSION
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.db.utils.generateId
import io.elephantchess.model.UserType
import io.elephantchess.utils.di.KoinSingleton
import io.github.oshai.kotlinlogging.KLogger
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock

/**
 * Deletes an authenticated account while keeping its (now identity-less) game, puzzle and rating data.
 *
 * On deletion a brand-new [UserType.ANONYMIZED] user is created with a fresh id and the original
 * ratings, but without any personal data (no email, username, password, country or description). All
 * rows that referenced the deleted user are re-pointed to the anonymized user, so even the user id
 * changes and can no longer be linked back to the person. Page views are aggregated into the archive
 * tables and their raw rows (and the user's sessions, which hold IP/location data) are deleted.
 *
 * Everything runs in a single transaction so the account either fully disappears or nothing changes.
 */
@KoinSingleton
class AccountDeletionDaoService(
    private val dslContext: DSLContext,
    private val userDaoService: UserDaoService,
    private val archivedPageViewDaoService: ArchivedPageViewDaoService,
    private val logger: KLogger,
) {

    /**
     * Deletes [userId] and transfers its data to a new anonymized user. Returns the new anonymized
     * user id, or `null` if the user does not exist.
     */
    suspend fun deleteAndAnonymize(userId: String): String? {
        val user = userDaoService.findById(userId)

        if (user == null) {
            logger.warn { "deleteAndAnonymize: user $userId not found — nothing to delete" }
            return null
        }

        val anonymizedId = generateUniqueUserId()

        dslContext.transactionCoroutine { cfg ->
            val tx = DSL.using(cfg)

            // 1. create the identity-less user that inherits the ratings (but no personal data)
            tx
                .insertInto(USER)
                .set(USER.ID, anonymizedId)
                .set(USER.CREATION, user.creation)
                .set(USER.LAST_ONLINE, Clock.System.now())
                .set(USER.USER_TYPE, UserType.ANONYMIZED)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_NEWSLETTER, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_USER_JOINED_GAME, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PLAYED_MOVE, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_RESIGNED, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PROPOSED_DRAW, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_ACCEPTED_DRAW, false)
                .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_DECLINED_DRAW, false)
                .set(USER.PUZZLE_RATING, user.puzzleRating)
                .set(USER.GAME_RATING_BULLET, user.gameRatingBullet)
                .set(USER.GAME_RATING_BLITZ, user.gameRatingBlitz)
                .set(USER.GAME_RATING_RAPID, user.gameRatingRapid)
                .set(USER.GAME_RATING_CLASSICAL, user.gameRatingClassical)
                .set(USER.GAME_RATING_SEVERAL_DAYS, user.gameRatingSeveralDays)
                .set(USER.GAME_RATING_CORRESPONDENCE, user.gameRatingCorrespondence)
                .set(USER.GAME_RATING_MANCHU_BULLET, user.gameRatingManchuBullet)
                .set(USER.GAME_RATING_MANCHU_BLITZ, user.gameRatingManchuBlitz)
                .set(USER.GAME_RATING_MANCHU_RAPID, user.gameRatingManchuRapid)
                .set(USER.GAME_RATING_MANCHU_CLASSICAL, user.gameRatingManchuClassical)
                .set(USER.GAME_RATING_MANCHU_SEVERAL_DAYS, user.gameRatingManchuSeveralDays)
                .set(USER.GAME_RATING_MANCHU_CORRESPONDENCE, user.gameRatingManchuCorrespondence)
                .awaitExecute()

            // 2. re-point every row that referenced the deleted user to the anonymized user
            reassignAllReferences(tx, userId, anonymizedId)

            // 3. archive the user's page views (aggregated) and drop the raw rows
            archivedPageViewDaoService.archivePageViews(tx, PAGE_VIEW_EVENT.USER_ID.eq(userId))
            tx.deleteFrom(PAGE_VIEW_EVENT).where(PAGE_VIEW_EVENT.USER_ID.eq(userId)).awaitExecute()

            // 4. drop the user's sessions (they hold IP/location data) and finally the user row
            tx.deleteFrom(USER_SESSION).where(USER_SESSION.USER_ID.eq(userId)).awaitExecute()
            tx.deleteFrom(USER).where(USER.ID.eq(userId)).awaitExecute()
        }

        logger.info { "deleted account $userId, data transferred to anonymized user $anonymizedId" }
        return anonymizedId
    }

    private suspend fun reassignAllReferences(tx: DSLContext, oldId: String, newId: String) {
        reassign(tx, GAME.INVITER, oldId, newId)
        reassign(tx, GAME.INVITEE, oldId, newId)
        reassign(tx, GAME.DRAW_PROPOSITION_USER, oldId, newId)

        reassign(tx, BOT_GAME.USER_ID, oldId, newId)
        reassign(tx, PUZZLE_RESULT.USER_ID, oldId, newId)
        reassign(tx, ANALYSIS.OWNER_USER_ID, oldId, newId)
        reassign(tx, GAME_STATUS_EVENT.USER_ID, oldId, newId)
        reassign(tx, GAME_CHAT_MESSAGE.AUTHOR, oldId, newId)
        reassign(tx, DISCORD_GAME_NOTIFICATION.USER_ID, oldId, newId)

        reassign(tx, GAME_SOCK_PUPPET_DETECTION.INVITER_USER_ID, oldId, newId)
        reassign(tx, GAME_SOCK_PUPPET_DETECTION.INVITEE_USER_ID, oldId, newId)

        reassign(tx, REFERENCE_GAME_SEARCH_QUERY.USER_ID, oldId, newId)
        reassign(tx, REFERENCE_PLAYER_PROFILE_EDIT.EDITOR_ID, oldId, newId)
        reassign(tx, REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.EDITOR_ID, oldId, newId)

        reassign(tx, KOFI_EVENT.MATCHED_USER_ID, oldId, newId)
        reassign(tx, UPCOMING_EVENT.CREATED_BY, oldId, newId)

        reassign(tx, SEVEN_KINGDOMS_GAME.PLAYER_WHITE, oldId, newId)
        reassign(tx, SEVEN_KINGDOMS_GAME.PLAYER_RED, oldId, newId)
        reassign(tx, SEVEN_KINGDOMS_GAME.PLAYER_ORANGE, oldId, newId)
        reassign(tx, SEVEN_KINGDOMS_GAME.PLAYER_BLUE, oldId, newId)
        reassign(tx, SEVEN_KINGDOMS_GAME.PLAYER_GREEN, oldId, newId)
        reassign(tx, SEVEN_KINGDOMS_GAME.PLAYER_PURPLE, oldId, newId)
        reassign(tx, SEVEN_KINGDOMS_GAME.PLAYER_BLACK, oldId, newId)
        reassign(tx, SEVEN_KINGDOMS_GAME_EVENT.USER_ID, oldId, newId)
    }

    /** Re-points a single user-referencing column from [oldId] to [newId]. */
    private suspend fun <R : Record> reassign(tx: DSLContext, field: TableField<R, String?>, oldId: String, newId: String) {
        tx
            .update(field.table)
            .set(field, newId)
            .where(field.eq(oldId))
            .awaitExecute()
    }

    private suspend fun generateUniqueUserId(): String {
        var id = generateId()
        while (userIdExists(id)) {
            id = generateId()
        }
        return id
    }

    private suspend fun userIdExists(id: String): Boolean =
        (
            dslContext
                .selectCount()
                .from(USER)
                .where(USER.ID.eq(id))
                .awaitSingleValue() ?: 0
            ) > 0
}
