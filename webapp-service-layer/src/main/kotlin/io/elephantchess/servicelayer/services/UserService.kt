package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.PasswordRecoveryAttempt
import io.elephantchess.db.dao.codegen.tables.pojos.User
import io.elephantchess.db.services.PasswordRecoveryAttemptsDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.*
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.ContactFormRequest
import io.elephantchess.servicelayer.dto.ValidatedResponse
import io.elephantchess.servicelayer.dto.user.*
import io.elephantchess.servicelayer.exceptions.NotAcceptableException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.exceptions.UnauthorizedException
import io.elephantchess.servicelayer.model.AuthenticatedToken
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.model.VerifiedToken
import io.elephantchess.servicelayer.services.TokenManager.Companion.RENEW_SESSION_INTERVAL
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRateStartImmediately
import io.elephantchess.utils.stripHtml
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import java.time.LocalDate
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class UserService(
    appConfig: AppConfig,
    private val passwordRecoveryRequestDaoService: PasswordRecoveryAttemptsDaoService,
    private val userDaoService: UserDaoService,
    private val userSessionService: UserSessionService,
    private val tokenManager: TokenManager,
    private val mailService: MailService,
    private val pageViewEventService: PageViewEventService,
    refresherScope: CoroutineScope,
    private val logger: KLogger,
) {

    @Volatile
    private var onlineUserIds: Set<String> = emptySet()

    // password hashing
    private val salt: ByteArray = appConfig.loadString("salt").toByteArray()
    private val secretKeyFactory = SecretKeyFactory.getInstance(SALT_ALGO)

    private val refreshJob = launchAtFixedRateStartImmediately(
        scope = refresherScope,
        period = 2.seconds,
        action = { refreshIsOnlineCache() }
    )

    fun cancel() {
        refreshJob.cancel()
    }

    /**
     * Visible for tests, maybe move the isOnline logic to a separate class that we could mock
     */
    suspend fun refreshIsOnlineCache() {
        val refreshed = userDaoService
            .listRecentlyActiveSeconds(15)
            .map { user -> user.id }
            .toHashSet()

        onlineUserIds = refreshed
    }

    suspend fun validateSignUp(request: SignUpRequest): ValidatedResponse<Unit> {
        val errors = validateSignUpRequest(request)
        return if (errors.isEmpty()) {
            ValidatedResponse.Valid(Unit)
        } else {
            if (logger.isDebugEnabled()) {
                errors.forEach { error ->
                    logger.debug { "validation error: $error" }
                }
            }
            ValidatedResponse.Invalid(errors)
        }
    }

    suspend fun signUp(request: SignUpRequest): ValidatedResponse<SignUpResponse> {
        val errors = validateSignUpRequest(request)

        val now = Clock.System.now()

        return if (errors.isEmpty()) {
            val user = User()
            user.id = generateId()
            user.handle = request.username.trim()
            user.email = request.email.trim()
            user.password = hash(request.password.toCharArray())
            user.creation = now
            user.lastOnline = now
            user.userType = UserType.AUTHENTICATED
            user.puzzleRating = PUZZLE_START_RATING
            userDaoService.save(user)
            mailService.sendNewUserNotification(user)
            mailService.verifyEmailAddressAsync(user.email)
            ValidatedResponse.Valid(
                SignUpResponse(
                    userId = user.id,
                    username = user.handle,
                    token = tokenManager.buildTokenForAuthenticatedUser(user)
                )
            )
        } else {
            errors.forEach { error ->
                logger.warn { "validation error: $error" }
            }
            ValidatedResponse.Invalid(errors)
        }
    }

    suspend fun obtainGuestUserToken(): ObtainAnonymousTokenResponse {
        val id = userDaoService.createGuestUser()
        logger.debug { "created guest #$id" }
        return ObtainAnonymousTokenResponse(
            id = id,
            token = tokenManager.buildTokenForGuestUser(id)
        )
    }

    suspend fun login(request: UserLoginRequest): SignedInUserResponse {
        val user = userDaoService.findByLogin(request.login)
        if (user == null) {
            throw NotFoundException("User not found ${request.login}")
        } else if (String(user.password) == String(hash(request.password.toCharArray()))) {
            userDaoService.updateLastOnline(user.id)
            return SignedInUserResponse(
                userId = user.id,
                username = user.handle,
                token = tokenManager.buildTokenForAuthenticatedUser(user),
                roles = user.roles
            )
        } else {
            throw UnauthorizedException("Incorrect credentials")
        }
    }

    /**
     * It used to be called with an interval in JS, but now it's only called on each page view to collect session info.
     * The regular ping now happens on WS "games to play" session.
     */
    suspend fun pingUserSession(
        verifiedToken: VerifiedToken,
        request: PingSessionRequest,
        remoteAddress: String,
        headers: Map<String, List<String>>,
    ): PingResponse {
        val userId = verifiedToken.userId

        // session
        userSessionService.handleUserSession(userId, remoteAddress, headers)

        // renew token
        var renewedTokenString: String? = null
        if (verifiedToken is AuthenticatedToken) {
            val expirationInDays = verifiedToken.expiresAtInDays()
            if (expirationInDays != null && expirationInDays <= RENEW_SESSION_INTERVAL) {
                // FIXME: why do we need to verify the token here? just to have the expiration date?
                renewedTokenString = tokenManager.buildTokenForAuthenticatedUser(userDaoService.findById(userId)!!)
                val renewedVerifiedToken = tokenManager.verifyToken(renewedTokenString) as VerifiedToken
                val oldExpirationTime = verifiedToken.expiresAtInstant()!!
                val newExpirationTime = renewedVerifiedToken.expiresAtInstant()!!
                userDaoService.updateSessionExpirationInfo(userId, oldExpirationTime, newExpirationTime)
                logger.info { "session has been renewed for $renewedVerifiedToken until $newExpirationTime" }
            }
        }

        // update last online
        userDaoService.updateLastOnline(userId)

        // handle page view event
        pageViewEventService.processPageViewEvent(verifiedToken, request.currentPage)

        return PingResponse(renewedTokenString)
    }

    suspend fun attemptPasswordRecovery(request: AttemptPasswordRecoveryRequest): PasswordRecoveryAttemptResponse {
        if (!isEmailFormatValid(request.email)) {
            throw NotAcceptableException("Invalid email")
        }

        val user = userDaoService.findByEmail(request.email)

        if (user != null) {
            if (mailService.isEmailAddressValid(request.email)) {
                val attemptRecord = PasswordRecoveryAttempt()
                attemptRecord.emailProvided = request.email
                attemptRecord.recoveryCode = randomAlphanumeric(64)
                attemptRecord.matchingUserId = user.id
                passwordRecoveryRequestDaoService.save(attemptRecord)

                mailService.sendPasswordRecoveryAttempt(user.email, attemptRecord.recoveryCode)
                return PasswordRecoveryAttemptResponse()
            } else {
                val msg = "'${request.email}' has not been validated"
                logger.debug { msg }
                throw NotAcceptableException(msg)
            }
        } else {
            val msg = "No matching user found for email '${request.email}'"
            logger.debug { msg }
            throw NotAcceptableException(msg)
        }
    }

    suspend fun finalizePasswordRecovery(request: FinalizePasswordRecoveryRequest): ValidatedResponse<Unit> {
        val attempt = passwordRecoveryRequestDaoService.fetchBy(request.email, request.code)
        if (attempt == null) {
            throw NotFoundException("No matching recovery for email ${request.email} and code")
        } else {
            val limit = attempt.entryCreation.plusHours(PASSWORD_RECOVERY_TIMEOUT_HOURS)
            val limitHasPassed = limit.isBefore(Clock.System.now())
            val error = validatePassword(request.newPassword)

            return if (limitHasPassed) {
                // expired
                ValidatedResponse.Invalid("Recovery code has expired")
            } else if (error != null) {
                // email not valid
                ValidatedResponse.Invalid(error)
            } else {
                // accepted
                userDaoService.updatePassword(attempt.matchingUserId!!, hash(request.newPassword.toCharArray()))
                passwordRecoveryRequestDaoService.updateRecoveryTime(attempt.id)
                mailService.sendPasswordRecoverySuccessful(attempt.emailProvided)
                ValidatedResponse.Valid(Unit)
            }
        }
    }

    suspend fun fetchProfile(username: String): UserProfile {
        // TODO: only fetch relevant fields
        val user = userDaoService.findByUserName(username)
        return if (user == null) {
            throw NotFoundException("User $username could not be found")
        } else {
            UserProfile(
                userId = user.id,
                username = user.handle,
                country = user.country,
                profileDescription = user.description,
                puzzleRating = user.puzzleRating
            )
        }
    }

    suspend fun fetchProfileSettings(userId: String): ProfileSettingsDto {
        val record = userDaoService.fetchProfileSettings(userId)
        if (record != null) {
            return ProfileSettingsDto(
                description = record.value1().orEmpty(),
                country = record.value2().orEmpty()
            )
        } else {
            throw NotFoundException("User not found")
        }
    }

    suspend fun fetchDescriptionByUserName(username: String) =
        userDaoService.fetchDescriptionByUsername(username)

    suspend fun updateProfileSettings(userId: String, request: ProfileSettingsDto) {
        // max 2 consecutive line breaks
        fun removeSuperfluousLineBreaks(input: String): String {
            val output = input.replace("[\r\n]{3}".toRegex(), "\n\n").trim()
            return if (output == input) output else removeSuperfluousLineBreaks(output)
        }

        if (request.description.length > MAX_DESCRIPTION_LENGTH) {
            throw NotAcceptableException("Description limited to $MAX_DESCRIPTION_LENGTH characters")
        }

        val description = stripHtml(removeSuperfluousLineBreaks(request.description))
        userDaoService.updateProfileSettings(userId, description, request.country)
    }

    suspend fun fetchNotificationsSettings(userId: String): NotificationsSettingsDto {
        val record = userDaoService.fetchNotificationSettings(userId)
            ?: throw NotFoundException("User not found")

        return NotificationsSettingsDto(
            newsletter = record.newsletter,
            opponentJoinedGame = record.opponentJoinedGame,
            opponentPlayedMove = record.opponentPlayedMove,
            opponentResigned = record.opponentResigned,
            opponentProposedDraw = record.opponentProposedDraw,
            opponentAcceptedDraw = record.opponentAcceptedDraw,
            opponentDeclinedDraw = record.opponentDeclinedDraw,
        )
    }

    suspend fun updateNotificationsSettings(userId: String, request: NotificationsSettingsDto) {
        userDaoService.updateNotificationSettings(
            userId = userId,
            newsletter = request.newsletter,
            opponentJoinedGame = request.opponentJoinedGame,
            opponentPlayedMove = request.opponentPlayedMove,
            opponentResigned = request.opponentResigned,
            opponentProposedDraw = request.opponentProposedDraw,
            opponentAcceptedDraw = request.opponentAcceptedDraw,
            opponentDeclinedDraw = request.opponentDeclinedDraw,
        )
    }

    suspend fun fetchEmailAddressSettings(userId: String): EmailAddressSettingsResponse {
        val email = userDaoService.fetchEmail(userId)
            ?: throw NotFoundException("email of user $userId could not be found")

        return EmailAddressSettingsResponse(
            email = email,
            isValid = mailService.getEmailValidityStatus(email),
        )
    }

    fun isOnline(userId: String): Boolean {
        return onlineUserIds.contains(userId)
    }

    fun areOnline(userIds: List<String>): AreUsersOnlineResponse {
        return AreUsersOnlineResponse(userIds.toSet().intersect(onlineUserIds))
    }

    fun countOnline(): Int {
        return onlineUserIds.size
    }

    suspend fun submitContact(request: ContactFormRequest, userId: UserId) {
        logger.info { "contact form submitted $request" }

        if (!isEmailFormatValid(request.email)) throw NotAcceptableException("invalid email")
        if (request.message.length > 1_000) throw NotAcceptableException("message too long")
        if (request.message.length < 10) throw NotAcceptableException("message too short")

        mailService.sendContactForm(
            userId = userId,
            email = request.email,
            message = request.message
        )
    }

    private suspend fun validateSignUpRequest(request: SignUpRequest): List<String> {
        val errors = mutableListOf<String>()

        if (request.username.length !in USERNAME_MIN_LENGTH..USERNAME_MAX_LENGTH) {
            errors += "Username must be between $USERNAME_MIN_LENGTH and $USERNAME_MAX_LENGTH char."
        }

        if (!isValidUsername(request.username)) {
            errors += "Username must be alphanumeric (can also include _ or -)"
        }

        if (!isEmailFormatValid(request.email)) {
            errors += "Invalid email format"
        }

        validatePassword(request.password)
            ?.let { error -> errors += error }

        if (userDaoService.existsForEmail(request.email)) {
            errors += "Email ${request.email} already taken"
        }

        if (userDaoService.existsForUsername(request.username)) {
            errors += "Username ${request.username} already taken"
        }

        return errors
    }

    private fun hash(password: CharArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 65536, 128)
        return secretKeyFactory.generateSecret(spec).encoded!!
    }

    suspend fun listUsersForSiteMap(): List<Pair<String, LocalDate>> {
        return userDaoService
            .listUsersWithDescriptionForSiteMap()
            .map { (username, lastProfileUpdate) ->
                username to lastProfileUpdate.toUtcLocalDate()
            }
    }

    companion object {

        const val PASSWORD_RECOVERY_TIMEOUT_HOURS = 1L
        const val SALT_ALGO = "PBKDF2WithHmacSHA1"

        const val USERNAME_MIN_LENGTH = 4
        const val USERNAME_MAX_LENGTH = 30

        const val PASSWORD_MIN_LENGTH = 4
        const val PASSWORD_MAX_LENGTH = 50

        const val PUZZLE_START_RATING = 800

        const val MAX_DESCRIPTION_LENGTH = 1_000

        private val EMAIL_REGEX = "^[A-Za-z0-9][^\\s]*@[^\\s]+\\.[^\\s]+$".toRegex()

        private fun validatePassword(password: String): String? {
            return if (password.length !in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH) {
                "Password must be between $PASSWORD_MIN_LENGTH and $PASSWORD_MAX_LENGTH char."
            } else {
                null
            }
        }

        /**
         * Allowed characters: alphanumeric, underscore and dashes
         */
        private fun isValidUsername(chars: String): Boolean =
            chars.matches("^[a-zA-Z0-9_-]*$".toRegex())

        private fun isEmailFormatValid(chars: String): Boolean =
            chars.matches(EMAIL_REGEX)

    }

}
