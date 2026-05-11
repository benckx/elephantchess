package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.User
import io.elephantchess.db.services.PasswordRecoveryAttemptsDaoService
import io.elephantchess.db.services.UserDaoService
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the online-user cache in [UserService].
 *
 * These tests use a pre-cancelled [CoroutineScope] so the periodic background
 * refresh never fires, giving each test full control over [UserService.refreshIsOnlineCache].
 */
class UserServiceOnlineCacheTest {

    private fun buildUserService(userDaoService: UserDaoService): UserService {
        val appConfig: AppConfig = mock()
        whenever(appConfig.loadString("salt")).thenReturn("testsalt1234")

        // A pre-cancelled scope prevents the background refresh job from ever running.
        val cancelledScope = CoroutineScope(Job().also { it.cancel() })

        return UserService(
            appConfig = appConfig,
            passwordRecoveryRequestDaoService = mock<PasswordRecoveryAttemptsDaoService>(),
            userDaoService = userDaoService,
            userSessionService = mock(),
            tokenManager = mock(),
            mailService = mock(),
            pageViewEventService = mock(),
            refresherScope = cancelledScope,
            logger = mock<KLogger>(),
        )
    }

    private fun userWith(id: String): User = User().also { it.id = id }

    @Test
    fun `refreshIsOnlineCache keeps previous snapshot when DB throws`() = runTest {
        val userDaoService: UserDaoService = mock()
        val userService = buildUserService(userDaoService)

        // First successful refresh – populate the cache
        whenever(userDaoService.listRecentlyActiveSeconds(any())).thenReturn(listOf(userWith("user1")))
        userService.refreshIsOnlineCache()

        assertEquals(1, userService.countOnline())
        assertTrue(userService.isOnline("user1"))

        // Second refresh fails – DB throws a transient error
        whenever(userDaoService.listRecentlyActiveSeconds(any())).thenAnswer { throw RuntimeException("DB error") }
        userService.refreshIsOnlineCache()

        // The previous snapshot must still be visible
        assertEquals(1, userService.countOnline(), "countOnline should retain snapshot after failed refresh")
        assertTrue(userService.isOnline("user1"), "isOnline should retain snapshot after failed refresh")
    }

    @Test
    fun `isOnline areOnline and countOnline reflect the latest successful snapshot`() = runTest {
        val userDaoService: UserDaoService = mock()
        val userService = buildUserService(userDaoService)

        whenever(userDaoService.listRecentlyActiveSeconds(any()))
            .thenReturn(listOf(userWith("alice"), userWith("bob")))
        userService.refreshIsOnlineCache()

        assertEquals(2, userService.countOnline())
        assertTrue(userService.isOnline("alice"))
        assertTrue(userService.isOnline("bob"))

        val response = userService.areOnline(listOf("alice", "bob", "charlie"))
        assertEquals(setOf("alice", "bob"), response.onlineUserIds)
    }
}
