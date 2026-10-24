package io.elephantchess.webapp.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.services.ExceptionService
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.*
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.util.concurrent.CancellationException as JavaCancellationException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class ExceptionHandlerTest {

    private val exceptionService = mock<ExceptionService>()
    private val simplePageRenderer = mock<SimplePageRenderer> {
        on { renderTemplate(eq("404"), any()) } doReturn "<html>not found page</html>"
        on { renderTemplate(eq("500"), any()) } doReturn "<html>error page</html>"
    }

    private val rootLogger = LoggerFactory.getLogger(ROOT_LOGGER_NAME) as Logger
    private val logEvents = ListAppender<ILoggingEvent>()

    private fun errorLogs() =
        logEvents.list.filter { it.level == Level.ERROR }
    private fun warnLogs() =
        logEvents.list.filter { it.level == Level.WARN }

    @BeforeTest
    fun setUp() {
        logEvents.start()
        rootLogger.addAppender(logEvents)
        startKoin {
            modules(
                module {
                    single { exceptionService }
                    single { simplePageRenderer }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        rootLogger.detachAppender(logEvents)
        stopKoin()
    }

    private fun testExceptionHandler(routes: Routing.() -> Unit = {}, block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                exceptionHandler()
                install(ContentNegotiation) { jackson() }
                routing(routes)
            }
            block()
        }

    private suspend fun ApplicationTestBuilder.getHtml(uri: String) =
        client.get(uri) { accept(ContentType.Text.Html) }

    // --- client disconnects ---

    @Test
    fun `isClientDisconnectException detects client disconnects in the cause chain`() {
        assertTrue(isClientDisconnectException(ClosedChannelException()))
        assertTrue(isClientDisconnectException(CancellationException("cancelled")))
        assertTrue(isClientDisconnectException(JavaCancellationException("HTTP upgrade has been cancelled")))
        assertTrue(isClientDisconnectException(IOException("Broken pipe")))
        assertTrue(isClientDisconnectException(IOException("Connection reset by peer")))
        assertTrue(isClientDisconnectException(UnsupportedOperationException("response was already completed")))
        assertTrue(isClientDisconnectException(RuntimeException("wrapper", IOException("Broken pipe"))))
    }

    @Test
    fun `isClientDisconnectException doesn't mute other exceptions`() {
        assertFalse(isClientDisconnectException(IOException("Disk full")))
        assertFalse(isClientDisconnectException(IllegalStateException("boom")))
        assertFalse(isClientDisconnectException(NullPointerException()))
        assertFalse(isClientDisconnectException(RuntimeException("wrapper", IllegalStateException("boom"))))
    }

    @Test
    fun `NPE thrown during a WebSocket upgrade is swallowed and not saved`() =
        testExceptionHandler({
            get("/ws/test") {
                // simulates Ktor Netty failing to upgrade because the client already disconnected, with the
                // pre-allocated NPE (no message, no stack trace) the JVM throws once the code is JIT-compiled
                call.response.status(HttpStatusCode.SwitchingProtocols)
                throw NullPointerException()
            }
        }) {
            client.get("/ws/test")
            // neither by our handler nor by Ktor (which logs exceptions rethrown by StatusPages)
            assertEquals(emptyList(), errorLogs())
            verifyNoInteractions(exceptionService)
        }

    @Test
    fun `NPE thrown outside of a WebSocket upgrade is a server error`() =
        testExceptionHandler({
            get("/test") { throw NullPointerException() }
        }) {
            val response = client.get("/test")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(errorLogs().isNotEmpty())
            verify(exceptionService).saveException(isA<NullPointerException>(), eq(500))
        }

    @Test
    fun `client disconnect wrapped in another exception is not saved`() =
        testExceptionHandler({
            get("/test") { throw RuntimeException("wrapper", IOException("Connection reset by peer")) }
        }) {
            runCatching { client.get("/test") }
            verifyNoInteractions(exceptionService)
        }

    @Test
    fun `Java CancellationException from HTTP upgrade cancellation is not saved`() =
        testExceptionHandler({
            get("/test") { throw JavaCancellationException("HTTP upgrade has been cancelled") }
        }) {
            runCatching { client.get("/test") }
            verifyNoInteractions(exceptionService)
        }

    // --- 404 ---

    @Test
    fun `unmapped URI returns the 404 page to browsers`() =
        testExceptionHandler {
            val response = getHtml("/does-not-exist")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("<html>not found page</html>", response.bodyAsText())
        }

    @Test
    fun `unmapped URI returns a JSON 404 to API clients`() =
        testExceptionHandler {
            val response = client.get("/does-not-exist")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertContains(response.bodyAsText(), "Not Found: /does-not-exist")
        }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/wp-login.php",
            "/.git-credentials",
            "/.ssh/id_rsa",
            "/.claude/settings.json",
            "/.vscode/mcp.json",
            "/backup/dump.sql",
            "/terraform.tfstate",
            "/ssl/server.key",
            "/vendor/composer/installed.json",
            "/@fs/proc/self/environ",
            "/static/..;/..;/proc/self/environ",
            "/var/run/secrets/kubernetes.io/serviceaccount/token",
            "/_profiler/phpinfo",
            "/actuator/health",
            "/api/config.json",
            "/firebase-config.js",
            "/onboarding",
            "/login",
            "/dashboard/billing",
            "/public/admin.json",
            "/.well-known/resource-that-should-not-exist-whose-status-code-should-not-be-200/",
        ],
    )
    fun `muted scanner probe returns a 404 without warning`(uri: String) =
        testExceptionHandler {
            val response = client.get(uri)
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(emptyList(), warnLogs())
            verifyNoInteractions(exceptionService)
        }

    @ParameterizedTest
    @ValueSource(strings = ["/does-not-exist", "/database/player/LiLi/unknown", "/js/modules/unknown.min.js.map"])
    fun `unmapped URI that doesn't look like a scanner probe is logged as a warning`(uri: String) =
        testExceptionHandler {
            val response = client.get(uri)
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(1, warnLogs().count { it.formattedMessage.startsWith("UNMAPPED URI") })
        }

    @Test
    fun `WebSocket path called without Upgrade header returns 426`() =
        testExceptionHandler {
            val response = client.get("/ws/pvp/games-to-play")
            assertEquals(HttpStatusCode.UpgradeRequired, response.status)
            assertContains(response.bodyAsText(), "WebSocket upgrade required")
        }

    // --- mapped exceptions ---

    @Test
    fun `HttpErrorException is mapped to its status code`() =
        testExceptionHandler({
            get("/test") { throw NotFoundException("Game xyz not found") }
        }) {
            val response = client.get("/test")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertContains(response.bodyAsText(), "Game xyz not found")
            verify(exceptionService).saveException(isA<NotFoundException>(), eq(404))
        }

    @Test
    fun `IllegalArgumentException is a bad request`() =
        testExceptionHandler({
            get("/test") { throw IllegalArgumentException("secret detail") }
        }) {
            val response = client.get("/test")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertFalse(response.bodyAsText().contains("secret detail"))
            verify(exceptionService).saveException(isA<IllegalArgumentException>(), eq(400))
        }

    @Test
    fun `IllegalStateException is a server error`() =
        testExceptionHandler({
            get("/test") { throw IllegalStateException("secret detail") }
        }) {
            val response = client.get("/test")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertFalse(response.bodyAsText().contains("secret detail"))
            verify(exceptionService).saveException(isA<IllegalStateException>(), eq(500))
        }

    @Test
    fun `path-traversal probe is a muted bad request, not logged as error or saved`() =
        testExceptionHandler({
            get("/test") {
                throw BadRequestException("Relative path should not contain path traversing characters: cache/original/../../.env")
            }
        }) {
            val response = client.get("/test")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(emptyList(), errorLogs())
            verifyNoInteractions(exceptionService)
        }

    @Test
    fun `other bad requests are still logged as errors and saved`() =
        testExceptionHandler({
            get("/test") { throw BadRequestException("malformed something") }
        }) {
            val response = client.get("/test")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(errorLogs().isNotEmpty())
            verify(exceptionService).saveException(isA<BadRequestException>(), eq(400))
        }

    @Test
    fun `unexpected exception returns the error page to browsers`() =
        testExceptionHandler({
            get("/test") { throw UnsupportedOperationException("secret detail") }
        }) {
            val response = getHtml("/test")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("<html>error page</html>", response.bodyAsText())
            verify(exceptionService).saveException(isA<UnsupportedOperationException>(), eq(500))
        }

    @Test
    fun `unexpected exception returns a generic JSON error to API clients`() =
        testExceptionHandler({
            get("/test") { throw UnsupportedOperationException("secret detail") }
        }) {
            val response = client.get("/test")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertContains(response.bodyAsText(), "Server error")
            assertFalse(response.bodyAsText().contains("secret detail"))
        }
}
