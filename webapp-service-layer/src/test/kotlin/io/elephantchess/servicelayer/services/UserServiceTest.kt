package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerification
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerificationBounce
import io.elephantchess.db.dao.codegen.tables.pojos.BotGame
import io.elephantchess.db.dao.codegen.tables.pojos.BotGameStatusEvent
import io.elephantchess.db.model.UserSessionRecord
import io.elephantchess.db.services.EmailVerificationDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.minusHours
import io.elephantchess.model.*
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.servicelayer.dto.ValidatedResponse
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.dto.user.*
import io.elephantchess.servicelayer.exceptions.UnauthorizedException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.model.VerifiedToken
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Color.RED
import kotlinx.coroutines.test.runTest
import org.apache.commons.lang3.RandomStringUtils.insecure
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class UserServiceTest : ServiceTest() {

    private val tokenManager by inject<TokenManager>()
    private val userDaoService by inject<UserDaoService>()
    private val userSessionDaoService by inject<UserSessionDaoService>()
    private val emailVerificationDaoService by inject<EmailVerificationDaoService>()
    private val pvpGameService by inject<PlayerVsPlayerGameService>()
    private val pvbGameDaoService by inject<PlayerVsBotGameDaoService>()
    private val dslContext by inject<DSLContext>()

    @AfterTest
    fun afterEach() = runTest {
        listOf(
            GAME_MOVE, GAME_STATUS_EVENT, GAME,
            BOT_GAME_MOVE, BOT_GAME_STATUS_EVENT, BOT_GAME,
            EMAIL_VERIFICATION, EMAIL_VERIFICATION_BOUNCE,
            REFERENCE_GAME_SEARCH_QUERY,
            USER_SESSION, PUZZLE_RESULT, USER, PUZZLE
        )
            .forEach { table ->
                dslContext
                    .deleteFrom(table)
                    .awaitExecute()
            }
    }

    @Test
    fun hashTest01() = runTest {
        val result = signUpTestUser()
        val email = result.first.email
        val username = result.first.username
        val password = result.first.password
        val userId = result.second

        // user can log in either with their email or username
        assertNotNull(userService.login(UserLoginRequest(email, password)))
        assertNotNull(userService.login(UserLoginRequest(username, password)))

        // email login is case-insensitive
        assertNotNull(userService.login(UserLoginRequest(email.uppercase(), password)))

        // user cannot log in with wrong password
        assertFailsWith<UnauthorizedException> {
            userService.login(
                UserLoginRequest(email, insecure().nextAlphanumeric(10))
            )
        }

        assertFailsWith<UnauthorizedException> {
            userService.login(
                UserLoginRequest(username, insecure().nextAlphanumeric(10))
            )
        }

        // userId can be extracted from token
        val token = userService.login(UserLoginRequest(email, password)).token
        assertEquals(userId, (tokenManager.verifyToken(token) as VerifiedToken).userId)
    }

    @Test
    fun `signUp should reject email with whitespace`() = runTest {
        val invalidEmails = listOf(
            "leeminh86@yahoo. com",    // space before TLD
            "lee minh86@yahoo.com",    // space in local part
            "leeminh86@ yahoo.com",    // space after @
            " leeminh86@yahoo.com",    // leading space
            "leeminh86@yahoo.com ",    // trailing space
            "lee\tminh86@yahoo.com",   // tab character
        )

        for (email in invalidEmails) {
            val request = SignUpRequest(
                username = "testuser${insecure().nextAlphanumeric(5)}",
                email = email,
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Invalid<Unit>>(result, "Email '$email' should be rejected")
            assertTrue(
                result.left().errors.contains("Invalid email format"),
                "Should contain 'Invalid email format' error for '$email'"
            )
        }
    }

    @Test
    fun `signUp should reject invalid email formats`() = runTest {
        val invalidEmails = listOf(
            "notanemail",              // no @ symbol
            "@nodomain.com",           // no local part
            "noat.com",                // missing @
            "missing@tld",             // no TLD
        )

        for (email in invalidEmails) {
            val request = SignUpRequest(
                username = "testuser${insecure().nextAlphanumeric(5)}",
                email = email,
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Invalid<Unit>>(result, "Email '$email' should be rejected")
            assertTrue(
                result.left().errors.contains("Invalid email format"),
                "Should contain 'Invalid email format' error for '$email'"
            )
        }
    }

    @Test
    fun `signUp should accept valid email formats`() = runTest {
        val validEmails = listOf(
            "test@example.com",
            "user.name@domain.org",
            "user+tag@domain.co.uk",
            "a1234@test.io",
        )

        for (email in validEmails) {
            val request = SignUpRequest(
                username = "testuser${insecure().nextAlphanumeric(5)}",
                email = email,
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Valid<Unit>>(result, "Email '$email' should be accepted")
        }
    }

    @Test
    fun `signUp should reject username that is too short`() = runTest {
        val request = SignUpRequest(
            username = "abc",  // 3 chars, minimum is 4
            email = "valid${insecure().nextAlphanumeric(5)}@example.com",
            password = "validPassword123"
        )
        val result = userService.validateSignUp(request)
        assertIs<ValidatedResponse.Invalid<Unit>>(result, "Username 'abc' should be rejected (too short)")
        assertTrue(
            result.left().errors.contains("Username must be between 4 and 30 char."),
            "Should contain username length error"
        )
    }

    @Test
    fun `signUp should reject username that is too long`() = runTest {
        val request = SignUpRequest(
            username = "a".repeat(31),  // 31 chars, maximum is 30
            email = "valid${insecure().nextAlphanumeric(5)}@example.com",
            password = "validPassword123"
        )
        val result = userService.validateSignUp(request)
        assertIs<ValidatedResponse.Invalid<Unit>>(result, "Username with 31 chars should be rejected (too long)")
        assertTrue(
            result.left().errors.contains("Username must be between 4 and 30 char."),
            "Should contain username length error"
        )
    }

    @Test
    fun `signUp should reject username with invalid characters`() = runTest {
        val invalidUsernames = listOf(
            "user name",       // space
            "user@name",       // @ symbol
            "user.name",       // dot
            "user!name",       // exclamation mark
            "用户名称",          // Chinese characters
            "user🙂name",      // emoji
        )

        for (username in invalidUsernames) {
            val request = SignUpRequest(
                username = username,
                email = "valid${insecure().nextAlphanumeric(5)}@example.com",
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Invalid<Unit>>(result, "Username '$username' should be rejected")
            assertTrue(
                result.left().errors.contains("Username must contain only letters, numbers, _ or -"),
                "Should contain invalid characters error for '$username'"
            )
        }
    }

    @Test
    fun `signUp should accept valid usernames`() = runTest {
        val validUsernames = listOf(
            "user",            // minimum length
            "username123",     // alphanumeric
            "user_name",       // with underscore
            "user-name",       // with dash
            "User_Name-123",   // mixed case with underscore and dash
            "nguyễn",          // vietnamese letters
            "trần",            // vietnamese letters with diacritics
            "Đặng_123",        // vietnamese letters with underscore and digits
            "a".repeat(30),    // maximum length
        )

        for (username in validUsernames) {
            val request = SignUpRequest(
                username = username,
                email = "valid${insecure().nextAlphanumeric(5)}@example.com",
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Valid<Unit>>(result, "Username '$username' should be accepted")
        }
    }

    @Test
    fun `signUp should reject password that is too short`() = runTest {
        val request = SignUpRequest(
            username = "validuser${insecure().nextAlphanumeric(5)}",
            email = "valid${insecure().nextAlphanumeric(5)}@example.com",
            password = "abc"  // 3 chars, minimum is 4
        )
        val result = userService.validateSignUp(request)
        assertIs<ValidatedResponse.Invalid<Unit>>(result, "Password 'abc' should be rejected (too short)")
        assertTrue(
            result.left().errors.contains("Password must be between 4 and 50 char."),
            "Should contain password length error"
        )
    }

    @Test
    fun `signUp should reject password that is too long`() = runTest {
        val request = SignUpRequest(
            username = "validuser${insecure().nextAlphanumeric(5)}",
            email = "valid${insecure().nextAlphanumeric(5)}@example.com",
            password = "a".repeat(51)  // 51 chars, maximum is 50
        )
        val result = userService.validateSignUp(request)
        assertIs<ValidatedResponse.Invalid<Unit>>(result, "Password with 51 chars should be rejected (too long)")
        assertTrue(
            result.left().errors.contains("Password must be between 4 and 50 char."),
            "Should contain password length error"
        )
    }

    @Test
    fun `signUp should accept valid passwords`() = runTest {
        val validPasswords = listOf(
            "abcd",            // minimum length (4)
            "a".repeat(50),    // maximum length (50)
            "Password123!",    // typical password
        )

        for (password in validPasswords) {
            val request = SignUpRequest(
                username = "validuser${insecure().nextAlphanumeric(5)}",
                email = "valid${insecure().nextAlphanumeric(5)}@example.com",
                password = password
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Valid<Unit>>(result, "Password '$password' should be accepted")
        }
    }

    @Test
    fun `fetchEmailAddressSettings should reflect the email validity status`() = runTest {
        val (request, userId) = signUpTestUser()

        // Before confirmation, no automated check has run, so status is UNKNOWN.
        val before = userService.fetchEmailAddressSettings(userId)
        assertEquals(request.email, before.email)
        assertEquals(EmailValidityStatus.UNKNOWN, before.validityStatus)
        assertTrue(before.canResendConfirmation)

        // After the user clicks the confirmation link, the email is MANUALLY_CONFIRMED.
        val code = userDaoService.findById(userId)!!.emailConfirmationCode
        assertTrue(userService.confirmEmail(code))

        val after = userService.fetchEmailAddressSettings(userId)
        assertEquals(EmailValidityStatus.MANUALLY_CONFIRMED, after.validityStatus)
        assertFalse(after.canResendConfirmation)
    }

    @Test
    fun `fetchEmailAddressSettings should allow resending confirmation after automated verification failures`() = runTest {
        val (request, userId) = signUpTestUser()

        val verification = EmailVerification()
        verification.id = insecure().nextAlphanumeric(12)
        verification.email = request.email
        verification.result = "invalid"
        verification.serviceName = EmailVerifierService.EMAIL_LIST_VERIFY
        verification.verificationTime = Clock.System.now()
        emailVerificationDaoService.save(verification)

        val automatedInvalid = userService.fetchEmailAddressSettings(userId)
        assertEquals(EmailValidityStatus.AUTOMATED_INVALID, automatedInvalid.validityStatus)
        assertTrue(automatedInvalid.canResendConfirmation)

        val bounce = EmailVerificationBounce()
        bounce.email = request.email
        bounce.bouncedTime = Clock.System.now()
        emailVerificationDaoService.insertOrUpdateBounced(listOf(bounce))

        val automatedBounced = userService.fetchEmailAddressSettings(userId)
        assertEquals(EmailValidityStatus.AUTOMATED_BOUNCED, automatedBounced.validityStatus)
        assertTrue(automatedBounced.canResendConfirmation)
    }

    @Test
    fun `updateProfileSettings should unset country when none is selected`() = runTest {
        val (request, userId) = signUpTestUser()

        userService.updateProfileSettings(userId, ProfileSettingsDto(description = "", country = "be"))
        userService.updateProfileSettings(userId, ProfileSettingsDto(description = "", country = "none"))

        val profile = userService.fetchProfile(request.username)
        assertNull(profile.country)

        val storedCountry = dslContext.fetchValueAsync(USER.COUNTRY, USER.ID.eq(userId))
        assertNull(storedCountry)
    }

    @Test
    fun `signUp should generate an email confirmation code and confirmEmail should mark the email as confirmed`() =
        runTest {
            val (_, userId) = signUpTestUser()

            // a confirmation code is generated at signup and the email is not yet confirmed
            val userAfterSignup = userDaoService.findById(userId)!!
            assertNotNull(userAfterSignup.emailConfirmationCode, "Confirmation code should be generated at signup")
            assertNull(userAfterSignup.emailConfirmedAt, "Email should not be confirmed yet")

            // confirming with an unknown code does nothing
            assertFalse(userService.confirmEmail("unknown-code"))
            assertNull(userDaoService.findById(userId)!!.emailConfirmedAt)

            // confirming with a blank code does nothing
            assertFalse(userService.confirmEmail(""))
            assertNull(userDaoService.findById(userId)!!.emailConfirmedAt)

            // confirming with the right code marks the email as confirmed
            assertTrue(userService.confirmEmail(userAfterSignup.emailConfirmationCode))
            val userAfterConfirmation = userDaoService.findById(userId)!!
            assertNotNull(userAfterConfirmation.emailConfirmedAt, "Email should be confirmed")

            // confirming again is idempotent
            val firstConfirmedAt = userAfterConfirmation.emailConfirmedAt
            assertTrue(userService.confirmEmail(userAfterSignup.emailConfirmationCode))
            assertEquals(firstConfirmedAt, userDaoService.findById(userId)!!.emailConfirmedAt)
        }

    @Test
    fun `confirmEmail should reject an expired confirmation code`() = runTest {
        val (_, userId) = signUpTestUser()
        val userAfterSignup = userDaoService.findById(userId)!!
        val code = userAfterSignup.emailConfirmationCode

        // simulate that the code was generated more than 1h ago
        userDaoService.updateEmailConfirmationCode(userId, code, Clock.System.now().minusHours(2L))

        assertFalse(userService.confirmEmail(code), "Expired code should be rejected")
        assertNull(userDaoService.findById(userId)!!.emailConfirmedAt)

        // after a resend, a new code is generated with a fresh timestamp and confirmation works again
        userService.resendEmailConfirmation(userId)
        val refreshed = userDaoService.findById(userId)!!
        assertNotEquals(code, refreshed.emailConfirmationCode, "Resend should generate a new code")
        assertTrue(userService.confirmEmail(refreshed.emailConfirmationCode))
        assertNotNull(userDaoService.findById(userId)!!.emailConfirmedAt)
    }

    @Test
    fun `resendEmailConfirmation should be a no-op for already-confirmed users`() = runTest {
        val (_, userId) = signUpTestUser()
        val originalCode = userDaoService.findById(userId)!!.emailConfirmationCode
        assertTrue(userService.confirmEmail(originalCode))

        userService.resendEmailConfirmation(userId)

        // code is unchanged since the user is already confirmed
        assertEquals(originalCode, userDaoService.findById(userId)!!.emailConfirmationCode)
    }

    @Test
    fun `fetchUserSessions should return latest sessions with total count`() = runTest {
        val userId = signUpTestUser().second

        repeat(6) { i ->
            userSessionDaoService.createOrUpdate(
                UserSessionRecord(
                    userId = userId,
                    remoteAddress = "1.2.3.${i + 1}",
                    userAgent = "agent-$i",
                    operatingSystemName = "os-$i",
                    agentName = "browser-$i",
                )
            )
        }

        val result = userService.fetchUserSessions(userId, limit = 5)

        assertEquals(6, result.total)
        assertEquals(5, result.entries.size)
    }

    @Test
    fun `deleteUserSessions should only delete selected sessions`() = runTest {
        val userId = signUpTestUser().second

        repeat(3) { i ->
            userSessionDaoService.createOrUpdate(
                UserSessionRecord(
                    userId = userId,
                    remoteAddress = "2.3.4.${i + 1}",
                    userAgent = "del-agent-$i",
                    operatingSystemName = "del-os-$i",
                    agentName = "del-browser-$i",
                )
            )
        }

        val sessionsBeforeDelete = userService.fetchUserSessions(userId, limit = 10)
        val selectedSessionIds = sessionsBeforeDelete.entries.take(2).map { it.id }

        val deleteResult = userService.deleteUserSessions(userId, DeleteUserSessionsRequest(selectedSessionIds))

        assertEquals(2, deleteResult.deletedCount)
        assertEquals(1, userService.fetchUserSessions(userId, limit = 10).entries.size)
    }

    @Test
    fun `signUp with guestUserId should transfer PvP games to new user`() = runTest {
        val guestId = UserId(GUEST, userService.obtainGuestUserToken().id)
        userService.refreshIsOnlineCache()

        val createGameRequest = CreateGameRequest(
            inviterColor = RED,
            isRated = false,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )
        val gameResponse = pvpGameService.createGame(guestId, createGameRequest)
        val gameId = gameResponse.gameId

        // Verify the game is initially owned by the guest
        val inviterBefore = dslContext.fetchValueAsync(GAME.INVITER, GAME.ID.eq(gameId))
        assertEquals(guestId.id, inviterBefore)

        // Sign up and pass the verified guest ID (simulating routing-level token extraction)
        val newUserId = signUpTestUser(transferGuestData = true, guestUserId = guestId.id).second

        // The PvP game should now be owned by the new authenticated user
        val inviterAfter = dslContext.fetchValueAsync(GAME.INVITER, GAME.ID.eq(gameId))
        assertEquals(newUserId, inviterAfter)

        // The original guest user ID should be recorded on the game row
        val guestUserIdAfter = dslContext.fetchValueAsync(GAME.GUEST_USER_ID, GAME.ID.eq(gameId))
        assertEquals(guestId.id, guestUserIdAfter)
    }

    @Test
    fun `signUp with guestUserId should transfer PvB games to new user`() = runTest {
        val guestId = UserId(GUEST, userService.obtainGuestUserToken().id)

        // Create a PvB game owned by the guest directly via the DAO (avoids running the engine)
        val gameId = insecure().nextAlphanumeric(12)
        val now = Clock.System.now()

        val gameRecord = BotGame().apply {
            id = gameId
            userId = guestId.id
            userColor = RED
            engine = Engine.FAIRYSTOCKFISH
            engineVersion = "11.2"
            depth = 4
            startFen = null
            gameStatus = GameEventType.CREATED
            currentFen = DEFAULT_START_FEN
            currentHalfMoveIndex = 0
            created = now
            lastUpdated = now
        }
        val statusRecord = BotGameStatusEvent().apply {
            botGameId = gameId
            eventType = GameEventType.CREATED
            eventTime = now
        }
        pvbGameDaoService.insertGame(gameRecord, statusRecord)

        // Verify the bot game is initially owned by the guest
        val userIdBefore = dslContext.fetchValueAsync(BOT_GAME.USER_ID, BOT_GAME.ID.eq(gameId))
        assertEquals(guestId.id, userIdBefore)

        // Sign up and pass the verified guest ID
        val newUserId = signUpTestUser(transferGuestData = true, guestUserId = guestId.id).second

        // The PvB game should now be owned by the new authenticated user
        val userIdAfter = dslContext.fetchValueAsync(BOT_GAME.USER_ID, BOT_GAME.ID.eq(gameId))
        assertEquals(newUserId, userIdAfter)

        // The original guest user ID should be recorded on the bot game row
        val guestUserIdAfter = dslContext.fetchValueAsync(BOT_GAME.GUEST_USER_ID, BOT_GAME.ID.eq(gameId))
        assertEquals(guestId.id, guestUserIdAfter)
    }

    @Test
    fun `signUp without guestUserId should not transfer any data`() = runTest {
        val guestId = UserId(GUEST, userService.obtainGuestUserToken().id)
        userService.refreshIsOnlineCache()

        val createGameRequest = CreateGameRequest(
            inviterColor = RED,
            isRated = false,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )
        val gameResponse = pvpGameService.createGame(guestId, createGameRequest)
        val gameId = gameResponse.gameId

        // Sign up without passing a guest user ID (no transfer)
        signUpTestUser()

        // The game should still belong to the guest
        val inviterAfter = dslContext.fetchValueAsync(GAME.INVITER, GAME.ID.eq(gameId))
        assertEquals(guestId.id, inviterAfter)

        val guestUserId = dslContext.fetchValueAsync(GAME.GUEST_USER_ID, GAME.ID.eq(gameId))
        assertNull(guestUserId, "Guest user ID should be null since no transfer occurred")
    }

    @Test
    fun `signUp with guestUserId should transfer puzzle rating to new user`() = runTest {
        val guestTokenResponse = userService.obtainGuestUserToken()
        val guestId = UserId(GUEST, guestTokenResponse.id)

        // Verify the guest starts with the default puzzle rating
        val guestRatingBefore = dslContext.fetchValueAsync(USER.PUZZLE_RATING, USER.ID.eq(guestId.id))
        assertEquals(800, guestRatingBefore)

        // Simulate the guest improving their puzzle rating
        dslContext.update(USER)
            .set(USER.PUZZLE_RATING, 950)
            .where(USER.ID.eq(guestId.id))
            .awaitExecute()

        // Sign up and transfer guest data
        val newUserId = signUpTestUser(transferGuestData = true, guestUserId = guestId.id).second

        // The new user should have the guest's puzzle rating
        val newUserRating = dslContext.fetchValueAsync(USER.PUZZLE_RATING, USER.ID.eq(newUserId))
        assertEquals(950, newUserRating)
    }

    @Test
    fun `signUp with guestUserId should transfer puzzle results to new user`() = runTest {
        val guestId = UserId(GUEST, userService.obtainGuestUserToken().id)

        // Insert a minimal puzzle row (required by the puzzle_result FK)
        val puzzleId = "testpzl1"
        dslContext.insertInto(PUZZLE)
            .set(PUZZLE.ID, puzzleId)
            .set(PUZZLE.REF_GAME_SOURCE, "test")
            .set(PUZZLE.REF_GAME_SOURCE_ID, "testSource1")
            .set(PUZZLE.ALGORITHM, PuzzleAlgo.FIND_PATH_TO_MATE)
            .set(PUZZLE.PLAYER_COLOR, RED)
            .set(PUZZLE.START_FEN, DEFAULT_START_FEN)
            .set(PUZZLE.INITIAL_RATING, 1000)
            .set(PUZZLE.RATING, 1000)
            .awaitExecute()

        // Insert a puzzle result for the guest
        dslContext.insertInto(PUZZLE_RESULT)
            .set(PUZZLE_RESULT.PUZZLE_ID, puzzleId)
            .set(PUZZLE_RESULT.USER_ID, guestId.id)
            .set(PUZZLE_RESULT.OUTCOME, PuzzleOutcome.SOLVED)
            .set(PUZZLE_RESULT.PUZZLE_RATING_FROM, 1000)
            .set(PUZZLE_RESULT.PUZZLE_RATING_TO, 1005)
            .set(PUZZLE_RESULT.PLAYER_RATING_FROM, 800)
            .set(PUZZLE_RESULT.PLAYER_RATING_TO, 810)
            .awaitExecute()

        // Sign up and transfer guest data
        val newUserId = signUpTestUser(transferGuestData = true, guestUserId = guestId.id).second

        // The puzzle result should now belong to the new user
        val userIdAfter = dslContext.fetchValueAsync(
            PUZZLE_RESULT.USER_ID,
            PUZZLE_RESULT.PUZZLE_ID.eq(puzzleId)
        )
        assertEquals(newUserId, userIdAfter)

        // The original guest user ID should be recorded on the puzzle result row
        val guestUserIdAfter = dslContext.fetchValueAsync(
            PUZZLE_RESULT.GUEST_USER_ID,
            PUZZLE_RESULT.PUZZLE_ID.eq(puzzleId)
        )
        assertEquals(guestId.id, guestUserIdAfter)
    }

    @Test
    fun `signUp with guestUserId should transfer reference game searches to new user`() = runTest {
        val guestId = UserId(GUEST, userService.obtainGuestUserToken().id)

        // Insert a reference game search query owned by the guest
        val queryId = insecure().nextAlphanumeric(12)
        val now = Clock.System.now()
        dslContext.insertInto(REFERENCE_GAME_SEARCH_QUERY)
            .set(REFERENCE_GAME_SEARCH_QUERY.QUERY_ID, queryId)
            .set(REFERENCE_GAME_SEARCH_QUERY.USER_ID, guestId.id)
            .set(REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME, now)
            .set(REFERENCE_GAME_SEARCH_QUERY.UPDATE_TIME, now)
            .set(REFERENCE_GAME_SEARCH_QUERY.PLAYER_NAME, "Hu Ronghua")
            .set(REFERENCE_GAME_SEARCH_QUERY.LIMIT, 20)
            .set(REFERENCE_GAME_SEARCH_QUERY.NUMBER_OF_RESULTS, 5)
            .awaitExecute()

        // Sign up and transfer guest data
        val newUserId = signUpTestUser(transferGuestData = true, guestUserId = guestId.id).second

        // The search query should now belong to the new user
        val userIdAfter = dslContext.fetchValueAsync(
            REFERENCE_GAME_SEARCH_QUERY.USER_ID,
            REFERENCE_GAME_SEARCH_QUERY.QUERY_ID.eq(queryId),
        )
        assertEquals(newUserId, userIdAfter)

        // The original guest user ID should be recorded on the search query row
        val guestUserIdAfter = dslContext.fetchValueAsync(
            REFERENCE_GAME_SEARCH_QUERY.GUEST_USER_ID,
            REFERENCE_GAME_SEARCH_QUERY.QUERY_ID.eq(queryId),
        )
        assertEquals(guestId.id, guestUserIdAfter)
    }

}
