package io.elephantchess.servicelayer.services

import io.elephantchess.servicelayer.dto.ValidatedResponse
import io.elephantchess.servicelayer.dto.user.SignUpRequest
import io.elephantchess.servicelayer.dto.user.UserLoginRequest
import io.elephantchess.servicelayer.exceptions.UnauthorizedException
import io.elephantchess.servicelayer.model.VerifiedToken
import kotlinx.coroutines.test.runTest
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.koin.core.component.inject
import kotlin.test.*

class UserServiceTest : ServiceTest() {

    private val tokenManager by inject<TokenManager>()

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
            "用户名称",           // non-ASCII characters
        )

        for (username in invalidUsernames) {
            val request = SignUpRequest(
                username = username,
                email = "valid${randomAlphanumeric(5)}@example.com",
                password = "validPassword123"
            )
            val result = userService.validateSignUp(request)
            assertIs<ValidatedResponse.Invalid<Unit>>(result, "Username '$username' should be rejected")
            assertTrue(result.left().errors.contains("Username must be alphanumeric (can also include _ or -)"), "Should contain invalid characters error for '$username'")
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
    fun `online users cache correctly reflects recently active users`() = runTest {
        val (_, userId) = signUpTestUser()

        userService.refreshIsOnlineCache()

        assertTrue(userService.isOnline(userId), "user should appear online after cache refresh")
        assertTrue(userService.countOnline() >= 1, "at least one user should be online")

        val response = userService.areOnline(listOf(userId))
        assertTrue(userId in response.onlineUserIds, "user should be in areOnline response")
    }

}
