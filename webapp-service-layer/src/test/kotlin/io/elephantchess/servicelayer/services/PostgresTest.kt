package io.elephantchess.servicelayer.services

import io.elephantchess.db.utils.awaitSingleValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.TableField
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(PER_CLASS)
abstract class PostgresTest {

    private val logger = KotlinLogging.logger {}

    protected val dbUser = "postgres"
    protected val dbPassword = "postgres"

    protected val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:18.4")
            .withDatabaseName("xiangqi")
            .withUsername(dbUser)
            .withPassword(dbPassword)
    }

    @BeforeAll
    open fun beforeAll() {
        container.start()
    }

    @AfterAll
    open fun afterAll() {
        logger.info { "stopping container" }
        container.stop()
    }

    /**
     * Shortcut for `select(field).from(field.table).where(condition).awaitSingleValue<T>()`.
     *
     * Useful in tests / one-off lookups where a single column value needs to be fetched
     * by a simple where condition.
     */
    suspend inline fun <reified T : Any> DSLContext.fetchValueAsync(
        field: TableField<*, T>,
        condition: Condition,
    ): T? {
        return select(field)
            .from(field.table)
            .where(condition)
            .awaitSingleValue()
    }

}
