package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.roles
import io.elephantchess.model.UserType
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.utils.CachedUser
import io.elephantchess.servicelayer.utils.NullableCache
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Store basic info in cache (username, userType, roles)
 */
class UserCache(
    private val userDaoService: UserDaoService,
    private val mailService: MailService,
) :
    NullableCache<String, CachedUser>(
        expireAfterWrite = 30.minutes,
        wontResolvePlaceHolder = playerHolder
    ) {

    override suspend fun loader(key: String): CachedUser? {
        return userDaoService
            .fetchBasicInformation(key)
            ?.let { userRecord ->
                CachedUser(
                    userId = userRecord.id,
                    userType = userRecord.userType,
                    username = when (userRecord.userType) {
                        UserType.AUTHENTICATED -> userRecord.handle
                        GUEST -> "guest #${userRecord.id}"
                        else -> UNKNOWN_USERNAME
                    },
                    creationTime = userRecord.creation,
                    roles = userRecord.roles
                )
            }
    }

    suspend fun fetchUsernameOrDefault(userId: UserId) =
        fetchUsernameOrDefault(userId.id)

    suspend fun fetchUsernameOrDefault(userId: String) =
        fetchUsername(userId) ?: UNKNOWN_USERNAME

    suspend fun fetchUsername(userId: String): String? =
        get(userId)?.username

    suspend fun fetchUserType(userId: String): UserType? =
        get(userId)?.userType

    suspend fun fetchCreationTime(userId: String): Instant? =
        get(userId)?.creationTime

    /**
     * Whether the user is allowed to use the "always show in lobby (also when I'm offline)" option when creating a
     * game. The option only makes sense if we can notify the user by email when someone joins their game while they
     * are offline, so it requires:
     * - a valid email address (manually confirmed or automatically verified), and
     * - the "someone joined my game" email notification to be enabled.
     *
     * Guest users have no email address and are therefore never allowed.
     */
    suspend fun isAlwaysVisibleInLobbyAllowed(userId: String): Boolean {
        val email = userDaoService.fetchEmail(userId) ?: return false
        if (!mailService.isEmailAddressValid(email)) {
            return false
        }
        val notificationSettings = userDaoService.fetchNotificationSettings(userId) ?: return false
        return notificationSettings.opponentJoinedGame
    }

    private companion object {

        const val UNKNOWN_USERNAME = "??"

        val playerHolder = CachedUser(
            userType = GUEST,
            userId = "??",
            username = "??",
            creationTime = Instant.DISTANT_PAST,
            roles = emptyList()
        )

    }

}
