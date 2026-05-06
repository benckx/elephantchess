package io.elephantchess.servicelayer.services

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(PER_CLASS)
abstract class PostgresTest {

    private val logger = KotlinLogging.logger {}

    protected val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:17.6")
            .withDatabaseName("xiangqi")
            .withUsername("postgres")
            .withPassword("postgres")
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

}
