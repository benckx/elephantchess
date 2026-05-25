package io.elephantchess.db.utils

import io.elephantchess.config.DbConfig
import io.elephantchess.db.migration.runDbMigration
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
import io.r2dbc.postgresql.client.SSLMode
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.ResourceAccessor
import org.apache.commons.lang3.RandomStringUtils.insecure
import org.jooq.DSLContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.conf.RenderNameCase.LOWER
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.net.URI
import java.time.Duration

private val logger = logger {}

fun generateId(size: Int = 12): String {
    fun convertLongToBase62(number: Long): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val result = StringBuilder()
        val bytes = number.toBigInteger().toByteArray()
        for (byte in bytes) {
            val digit = characters[byte.toInt() and characters.length - 1]
            result.append(digit)
        }
        return result.toString()
    }

    var id = convertLongToBase62(System.nanoTime())
    id += insecure().nextAlphanumeric(size - id.length)
    return id
}

fun getDslContext(
    dbConfig: DbConfig,
    maximumPoolSize: Int = 3,
    resourceAccessor: ResourceAccessor = ClassLoaderResourceAccessor(),
): DSLContext {
    val requireSsl = dbConfig.url.contains("ondigitalocean.com")

    // Add SSL parameters to JDBC URL if needed
    val jdbcUrl = if (requireSsl && !dbConfig.url.contains("sslmode=")) {
        val separator = if (dbConfig.url.contains("?")) "&" else "?"
        "${dbConfig.url}${separator}sslmode=require"
    } else {
        dbConfig.url
    }

    // run Liquibase migrations using JDBC
    runDbMigration(
        url = jdbcUrl,
        user = dbConfig.user,
        password = dbConfig.password,
        resourceAccessor = resourceAccessor
    )

    // create R2DBC-based DSLContext
    val connectionFactory = getR2dbcConnectionFactory(dbConfig, maximumPoolSize, requireSsl)
    val settings = Settings().withRenderNameCase(LOWER).withRenderSchema(false)!!
    return DSL.using(connectionFactory, POSTGRES, settings)
}

private fun getR2dbcConnectionFactory(
    dbConfig: DbConfig,
    maxPoolSize: Int,
    requireSsl: Boolean
): ConnectionFactory {
    val url = dbConfig.url
    val cleaned = if (url.startsWith("jdbc:")) url.removePrefix("jdbc:") else url
    val uri = URI(cleaned)
    val host = uri.host ?: throw IllegalArgumentException("missing host in db url")
    val port = if (uri.port == -1) 5432 else uri.port
    val database = uri.path?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: dbConfig.dbName

    logger.info { "host = $host:$port, database = $database, user = ${dbConfig.user}, ssl = $requireSsl" }

    if (host.contains("ondigitalocean.com")) {
        logger.warn { ">>> USING PROD DATABASE <<<" }
    }

    val connectionFactoryOptions =
        ConnectionFactoryOptions
            .builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, host)
            .option(ConnectionFactoryOptions.PORT, port)
            .option(ConnectionFactoryOptions.DATABASE, database)
            .option(ConnectionFactoryOptions.USER, dbConfig.user)
            .option(ConnectionFactoryOptions.PASSWORD, dbConfig.password)
            .apply {
                if (requireSsl) {
                    option(ConnectionFactoryOptions.SSL, true)
                    option(PostgresqlConnectionFactoryProvider.SSL_MODE, SSLMode.REQUIRE)
                }
            }
            .build()

    val connectionPoolConfiguration =
        ConnectionPoolConfiguration
            .builder(ConnectionFactories.get(connectionFactoryOptions))
            .maxIdleTime(Duration.ofMinutes(30))
            .maxSize(maxPoolSize)
            .build()

    return ConnectionPool(connectionPoolConfiguration)
}
