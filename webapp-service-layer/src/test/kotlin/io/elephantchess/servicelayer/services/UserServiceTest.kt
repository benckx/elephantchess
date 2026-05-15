package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.model.UserSessionRecord
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.model.TimeControlMode
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.servicelayer.dto.ValidatedResponse
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.dto.user.DeleteUserSessionsRequest
import io.elephantchess.servicelayer.dto.user.SignUpRequest
import io.elephantchess.servicelayer.dto.user.UserLoginRequest
import io.elephantchess.servicelayer.exceptions.UnauthorizedException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.model.VerifiedToken
import io.elephantchess.xiangqi.Color.RED
import kotlinx.coroutines.test.runTest
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class UserServiceTest : ServiceTest() {

    private val tokenManager by inject<TokenManager>()
    private val userSessionDaoService by inject<UserSessionDaoService>()
    private val pvpGameService by inject<PlayerVsPlayerGameService>()
    private val dslContext by inject<DSLContext>()

    @AfterTest
    fun afterEach() = runTest {
        listOf(GAME_MOVE, GAME_STATUS_EVENT, GAME, USER_SESSION, USER)
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
            userService.login(UserLoginRequest(email, randomAlphanumeric(10)))
        }

        assertFailsWith<UnauthorizedException> {
            userService.login(UserLoginRequest(username, randomAlphanumeric(10)))
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
                username = "testuser${randomAlphanumeric(5)}",
                email = email,
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Invalid<Unit>>(result, "Email '$email' should be rejected")
            assertTrue(result.left().errors.contains("Invalid email format"), "Should contain 'Invalid email format' error for '$email'")
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
                username = "testuser${randomAlphanumeric(5)}",
                email = email,
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Invalid<Unit>>(result, "Email '$email' should be rejected")
            assertTrue(result.left().errors.contains("Invalid email format"), "Should contain 'Invalid email format' error for '$email'")
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
                username = "testuser${randomAlphanumeric(5)}",
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
            email = "valid${randomAlphanumeric(5)}@example.com",
            password = "validPassword123"
        )
        val result = userService.validateSignUp(request)
        assertIs<ValidatedResponse.Invalid<Unit>>(result, "Username 'abc' should be rejected (too short)")
        assertTrue(result.left().errors.contains("Username must be between 4 and 30 char."), "Should contain username length error")
    }

    @Test
    fun `signUp should reject username that is too long`() = runTest {
        val request = SignUpRequest(
            username = "a".repeat(31),  // 31 chars, maximum is 30
            email = "valid${randomAlphanumeric(5)}@example.com",
            password = "validPassword123"
        )
        val result = userService.validateSignUp(request)
        assertIs<ValidatedResponse.Invalid<Unit>>(result, "Username with 31 chars should be rejected (too long)")
        assertTrue(result.left().errors.contains("Username must be between 4 and 30 char."), "Should contain username length error")
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
                email = "valid${randomAlphanumeric(5)}@example.com",
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Invalid<Unit>>(result, "Username '$username' should be rejected")
            assertTrue(result.left().errors.contains("Username must contain only letters, numbers, _ or -"), "Should contain invalid characters error for '$username'")
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
                email = "valid${randomAlphanumeric(5)}@example.com",
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Valid<Unit>>(result, "Username '$username' should be accepted")
        }
    }

    @Test
    fun `signUp should reject password that is too short`() = runTest {
        val request = SignUpRequest(
            username = "validuser${randomAlphanumeric(5)}",
            email = "valid${randomAlphanumeric(5)}@example.com",
            password = "abc"  // 3 chars, minimum is 4
        )
        val result = userService.validateSignUp(request)
        assertIs<ValidatedResponse.Invalid<Unit>>(result, "Password 'abc' should be rejected (too short)")
        assertTrue(result.left().errors.contains("Password must be between 4 and 50 char."), "Should contain password length error")
    }

    @Test
    fun `signUp should reject password that is too long`() = runTest {
        val request = SignUpRequest(
            username = "validuser${randomAlphanumeric(5)}",
            email = "valid${randomAlphanumeric(5)}@example.com",
            password = "a".repeat(51)  // 51 chars, maximum is 50
        )
        val result = userService.validateSignUp(request)
        assertIs<ValidatedResponse.Invalid<Unit>>(result, "Password with 51 chars should be rejected (too long)")
        assertTrue(result.left().errors.contains("Password must be between 4 and 50 char."), "Should contain password length error")
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
                username = "validuser${randomAlphanumeric(5)}",
                email = "valid${randomAlphanumeric(5)}@example.com",
                password = password
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Valid<Unit>>(result, "Password '$password' should be accepted")
        }
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
        val inviterBefore = dslContext.select(GAME.INVITER)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue<String>()
        assertEquals(guestId.id, inviterBefore)

        // Sign up and pass the verified guest ID (simulating routing-level token extraction)
        val i = randomAlphanumeric(8)
        val request = SignUpRequest(
            username = "xfer$i",
            email = "xfer$i@example.com",
            password = "password",
            transferGuestData = true
        )
        val newUserId = userService.signUp(request, guestUserId = guestId.id).right().userId

        // The PvP game should now be owned by the new authenticated user
        val inviterAfter = dslContext.select(GAME.INVITER)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue<String>()
        assertEquals(newUserId, inviterAfter)

        // The original guest user ID should be recorded on the game row
        val guestUserIdAfter = dslContext.select(GAME.GUEST_USER_ID)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue<String>()
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
        val i = randomAlphanumeric(8)
        val request = SignUpRequest(
            username = "noxfer$i",
            email = "noxfer$i@example.com",
            password = "password"
        )
        userService.signUp(request)

        // The game should still belong to the guest
        val inviterAfter = dslContext.select(GAME.INVITER)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue<String>()
        assertEquals(guestId.id, inviterAfter)
    }

    @Test
    fun `signUp with guestUserId should transfer puzzle rating to new user`() = runTest {
        val guestTokenResponse = userService.obtainGuestUserToken()
        val guestId = UserId(GUEST, guestTokenResponse.id)

        // Verify the guest starts with the default puzzle rating
        val guestRatingBefore = dslContext.select(USER.PUZZLE_RATING)
            .from(USER)
            .where(USER.ID.eq(guestId.id))
            .awaitSingleValue<Int>()
        assertEquals(800, guestRatingBefore)

        // Simulate the guest improving their puzzle rating
        dslContext.update(USER)
            .set(USER.PUZZLE_RATING, 950)
            .where(USER.ID.eq(guestId.id))
            .awaitExecute()

        // Sign up and transfer guest data
        val i = randomAlphanumeric(8)
        val request = SignUpRequest(
            username = "elouser$i",
            email = "elouser$i@example.com",
            password = "password",
            transferGuestData = true
        )
        val newUserId = userService.signUp(request, guestUserId = guestId.id).right().userId

        // The new user should have the guest's puzzle rating
        val newUserRating = dslContext.select(USER.PUZZLE_RATING)
            .from(USER)
            .where(USER.ID.eq(newUserId))
            .awaitSingleValue<Int>()
        assertEquals(950, newUserRating)
    }

}