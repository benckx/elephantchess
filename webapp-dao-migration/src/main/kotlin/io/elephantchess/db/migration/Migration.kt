package io.elephantchess.db.migration

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ResourceAccessor
import java.sql.DriverManager
import kotlin.system.measureTimeMillis

private val logger = logger {}

fun runDbMigration(url: String, user: String, password: String, resourceAccessor: ResourceAccessor) {
    logger.info { "running Liquibase using JDBC $user@$url" }

    DriverManager
        .getConnection(url, user, password)
        .use { connection ->
            val liquibaseDatabase =
                DatabaseFactory
                    .getInstance()
                    .findCorrectDatabaseImplementation(JdbcConnection(connection))

            val liquibase = Liquibase("liquibase-changelog.xml", resourceAccessor, liquibaseDatabase)
            val ms = measureTimeMillis { liquibase.update(Contexts()) }
            logger.info { "liquibase update completed in $ms ms." }
        }
}
