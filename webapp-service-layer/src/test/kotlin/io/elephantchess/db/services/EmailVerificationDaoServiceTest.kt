package io.elephantchess.db.services

import io.elephantchess.config.DbConfig
import io.elephantchess.db.dao.codegen.Tables.EMAIL_VERIFICATION
import io.elephantchess.db.dao.codegen.Tables.EMAIL_VERIFICATION_BOUNCE
import io.elephantchess.db.dao.codegen.Tables.NEWSLETTER
import io.elephantchess.db.dao.codegen.Tables.NEWSLETTER_EMAIL
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerification
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerificationBounce
import io.elephantchess.db.dao.codegen.tables.pojos.NewsletterEmail
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.db.utils.getDslContext
import io.elephantchess.model.EmailVerifierService
import io.elephantchess.model.SuppressingEmailStatus
import io.elephantchess.servicelayer.services.PostgresTest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeAll
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class EmailVerificationDaoServiceTest : PostgresTest() {

    private lateinit var dsl: DSLContext
    private lateinit var dao: EmailVerificationDaoService
    private val time = Instant.parse("2026-11-10T12:00:00Z")

    @BeforeAll
    override fun beforeAll() {
        super.beforeAll()
        dsl = getDslContext(DbConfig("postgres", container.jdbcUrl, dbUser, dbPassword))
        dao = EmailVerificationDaoService(dsl, KotlinLogging.logger {})
    }

    @AfterTest
    fun cleanBounces() =
        runTest {
            dsl.deleteFrom(NEWSLETTER_EMAIL).awaitExecute()
            dsl.deleteFrom(NEWSLETTER).awaitExecute()
            dsl.deleteFrom(EMAIL_VERIFICATION_BOUNCE).awaitExecute()
            dsl.deleteFrom(EMAIL_VERIFICATION).awaitExecute()
        }

    @Test
    fun `bounce and verification writes trim addresses without changing case`() =
        runTest {
            dao.insertOrUpdateBounced(listOf(bounce("  Recipient@example.org\t", "5.1.1")))
            assertEquals("Recipient@example.org", dao.listBounceRecords(100, 0).single().email)
            dao.insertOrUpdateBounced(listOf(bounce("\tRecipient@example.org ", "5.7.1")))
            assertEquals(1, dao.listBounceRecords(100, 0).size)
            assertEquals("5.7.1", dao.listBounceRecords(100, 0).single().status)
            dao.save(
                EmailVerification().apply {
                    id = "trim-test"
                    email = "  Verified@example.org\t"
                    verificationTime = time
                    serviceName = EmailVerifierService.EMAIL_LIST_VERIFY
                    result = "ok"
                },
            )
            assertEquals(
                "Verified@example.org",
                dsl.select(EMAIL_VERIFICATION.EMAIL).from(EMAIL_VERIFICATION).awaitSingleValue<String>(),
            )
        }

    @Test
    fun `migration preserves legacy rows and supports old writers and rollback`() {
        DriverManager.getConnection(container.jdbcUrl, dbUser, dbPassword).use { connection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase("liquibase-changelog.xml", ClassLoaderResourceAccessor(), database).use { liquibase ->
                liquibase.rollback(1, "")
                try {
                    connection.createStatement().use {
                        it.executeUpdate(
                            "INSERT INTO email_verification_bounce (email, bounced_time) VALUES ('legacy@example.org', CURRENT_TIMESTAMP)",
                        )
                    }
                    connection.commit()
                    liquibase.update(Contexts())
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT * FROM email_verification_bounce WHERE email = 'legacy@example.org'").use {
                            assertTrue(it.next())
                            assertNull(it.getString("action"))
                            assertNull(it.getString("status"))
                            assertNull(it.getString("diagnostic"))
                        }
                        statement.executeUpdate(
                            "INSERT INTO email_verification_bounce (email, bounced_time) VALUES ('old-writer@example.org', CURRENT_TIMESTAMP)",
                        )
                    }
                    connection.commit()
                    liquibase.rollback(1, "")
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT count(*) FROM email_verification_bounce").use {
                            assertTrue(it.next())
                            assertEquals(2, it.getInt(1))
                        }
                    }
                } finally {
                    liquibase.update(Contexts())
                }
            }
        }
    }

    @Test
    fun `only configured statuses and legacy records suppress email`() =
        runTest {
            val blockedCodes = listOf("5.1.1", "5.1.3", "5.1.0", "5.2.1", "5.2.2", "4.4.3", "5.0.0")
            val allowedCodes = listOf("5.7.1", "4.2.2", "4.4.1", "5.4.4", "5.5.0", "9.9.9")
            assertEquals(blockedCodes.toSet(), SuppressingEmailStatus.codes)
            for (status in blockedCodes + allowedCodes) {
                val email = "$status@example.org"
                dao.insertOrUpdateBounced(listOf(bounce(email, status)))
                assertEquals(status in blockedCodes, dao.hasSuppressingBounce(email))
                assertFalse(dao.hasSuppressingBounce(" $email "))
                assertFalse(dao.hasSuppressingBounce(email.uppercase()))
            }
            dao.insertOrUpdateBounced(listOf(bounce("legacy@example.org", null)))
            assertTrue(dao.hasSuppressingBounce("legacy@example.org"))
            assertFalse(dao.hasSuppressingBounce("absent@example.org"))
            assertEquals(
                (blockedCodes.map { "$it@example.org" } + "legacy@example.org").toSet(),
                dao.listSuppressedEmails().toSet(),
            )
            assertEquals(blockedCodes.size + allowedCodes.size + 1, dao.listBouncedEmails().size)
        }

    @Test
    fun `updates existing bounce with metadata and ignores older duplicate reports`() =
        runTest {
            dao.insertOrUpdateBounced(listOf(bounce("recipient@example.org", null)))
            val enriched = bounce("recipient@example.org", "5.7.1")
            dao.insertOrUpdateBounced(listOf(enriched, enriched))
            val records = dao.listBounceRecords(100, 0)
            assertEquals(1, records.size)
            assertEquals("failed", records.single().action)
            assertEquals("5.7.1", records.single().status)
            assertEquals("smtp; 550 policy rejection", records.single().diagnostic)
            assertFalse(dao.hasSuppressingBounce("recipient@example.org"))

            val older = bounce("recipient@example.org", "5.1.1").apply {
                bouncedTime = Instant.parse("2026-11-09T12:00:00Z")
            }
            dao.insertOrUpdateBounced(listOf(older))
            assertEquals("5.7.1", dao.listBounceRecords(100, 0).single().status)
            val legacyImport = bounce("recipient@example.org", null).apply {
                bouncedTime = Instant.parse("2026-11-11T12:00:00Z")
            }
            dao.insertOrUpdateBounced(listOf(legacyImport))
            assertEquals("5.7.1", dao.listBounceRecords(100, 0).single().status)
            assertEquals(time, dao.listBounceRecords(100, 0).single().bouncedTime)
        }

    @Test
    fun `suppressed queued recipients do not starve the newsletter batch`() =
        runTest {
            dsl.insertInto(NEWSLETTER)
                .set(NEWSLETTER.ID, "newsletter")
                .set(NEWSLETTER.TEMPLATE_NAME, "template")
                .set(NEWSLETTER.SUBJECT, "subject")
                .awaitExecute()
            val newsletterDao = NewsletterDaoService(dsl)
            val emails = listOf("blocked@example.org", "legacy@example.org", "allowed@example.org")
            newsletterDao.insertAll(
                emails.map {
                    NewsletterEmail().apply {
                        newsletterId = "newsletter"
                        emailAddress = " $it\t"
                        createdTime = time
                        unsubscribeFromNewsletterCode = UUID.randomUUID().toString()
                        unsubscribeFromAllCode = UUID.randomUUID().toString()
                    }
                },
            )
            dao.insertOrUpdateBounced(
                listOf(
                    bounce("blocked@example.org", "5.1.1"),
                    bounce("legacy@example.org", null),
                    bounce("allowed@example.org", "5.7.1"),
                ),
            )
            assertEquals(listOf("allowed@example.org"), newsletterDao.getBatchToSend(2).map { it.emailAddress })
        }

    @Test
    fun `lists records newest first with stable pagination`() =
        runTest {
            dao.insertOrUpdateBounced(
                listOf(
                    bounce("b@example.org", "5.1.1"),
                    bounce("a@example.org", "5.7.1"),
                    bounce("old@example.org", null).apply { bouncedTime = Instant.parse("2025-01-01T00:00:00Z") },
                ),
            )
            assertEquals(listOf("a@example.org", "b@example.org"), dao.listBounceRecords(2, 0).map { it.email })
            assertEquals(listOf("old@example.org"), dao.listBounceRecords(2, 2).map { it.email })
        }

    private fun bounce(email: String, status: String?): EmailVerificationBounce =
        EmailVerificationBounce().apply {
            this.email = email
            bouncedTime = time
            action = if (status == null) null else "failed"
            this.status = status
            diagnostic = if (status == null) null else "smtp; 550 policy rejection"
        }
}
