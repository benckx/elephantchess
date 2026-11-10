package io.elephantchess.webapp.routing

import io.elephantchess.model.UserRole.ADMIN
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.servicelayer.dto.admin.EmailBouncesResponse
import io.elephantchess.servicelayer.model.AuthenticatedToken
import io.elephantchess.servicelayer.services.TokenManager
import io.elephantchess.servicelayer.services.UserCache
import io.elephantchess.servicelayer.services.admin.AdminEmailBounceService
import io.elephantchess.servicelayer.utils.CachedUser
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.elephantchess.webapp.routing.api.adminConsoleRoutes
import io.elephantchess.webapp.routing.html.adminPagesRouting
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson3.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Instant

class EmailBouncesAdminTest {

    @Test
    fun `bounce API and page require admin and return paginated metadata`() =
        testApplication {
            val tokens = mock<TokenManager>()
            val users = mock<UserCache>()
            val bounces = mock<AdminEmailBounceService>()
            val renderer = mock<SimplePageRenderer>()
            val admin = mock<AuthenticatedToken> {
                on { userId } doReturn "admin"
                on { hasRole(ADMIN) } doReturn true
            }
            val ordinary = mock<AuthenticatedToken> {
                on { hasRole(ADMIN) } doReturn false
            }
            whenever(tokens.verifyToken("admin")).thenReturn(admin)
            whenever(tokens.verifyToken("ordinary")).thenReturn(ordinary)
            startKoin {
                modules(
                    module {
                        single { tokens }
                        single { users }
                        single { bounces }
                        single { renderer }
                    },
                )
            }
            try {
                application {
                    install(ContentNegotiation) { jackson() }
                    routing {
                        adminConsoleRoutes()
                        adminPagesRouting()
                    }
                }
                val api = "/api/admin/analytics/email-bounces"
                for (path in listOf(api, "/admin/email-bounces")) {
                    assertEquals(HttpStatusCode.Forbidden, client.get(path).status)
                    assertEquals(
                        HttpStatusCode.Forbidden,
                        client.get(path) { bearerAuth("ordinary") }.status,
                    )
                }
                verifyNoInteractions(bounces, renderer)

                whenever(users.get("admin")).thenReturn(
                    CachedUser(AUTHENTICATED, "admin", "admin", Instant.DISTANT_PAST, listOf(ADMIN)),
                )
                whenever(bounces.listBounces(100)).thenReturn(
                    EmailBouncesResponse(
                        listOf(
                            EmailBouncesResponse.Entry(
                                email = "recipient@example.org",
                                bouncedTime = 123L,
                                action = "failed",
                                status = "5.7.1",
                                diagnostic = "smtp; 550 <policy rejection>",
                                suppressed = false,
                            ),
                        ),
                        false,
                    ),
                )
                val response = client.get("$api?offset=100") { bearerAuth("admin") }
                assertEquals(HttpStatusCode.OK, response.status)
                assertContains(response.bodyAsText(), "\"diagnostic\":\"smtp; 550 <policy rejection>\"")
                assertContains(response.bodyAsText(), "\"suppressed\":false")
                for (offset in listOf("-1", "invalid", "2147483648")) {
                    assertEquals(
                        HttpStatusCode.BadRequest,
                        client.get("$api?offset=$offset") { bearerAuth("admin") }.status,
                    )
                }
                whenever(renderer.renderTemplate(eq("admin/admin_email_bounces"), any())).thenReturn("<h1>Email Bounces</h1>")
                val page = client.get("/admin/email-bounces") { bearerAuth("admin") }
                assertEquals(HttpStatusCode.OK, page.status)
                assertContains(page.bodyAsText(), "Email Bounces")
            } finally {
                stopKoin()
            }
        }
}
