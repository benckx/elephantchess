package io.elephantchess.scripts.utils

import io.elephantchess.config.AppConfig
import io.elephantchess.config.DbConfig
import io.elephantchess.db.utils.getDslContext
import liquibase.resource.DirectoryResourceAccessor
import org.jooq.DSLContext
import java.io.File

fun getScriptDslContext(appConfig: AppConfig, maximumPoolSize: Int = 2): DSLContext {
    return getScriptDslContext(appConfig.dbConfig, maximumPoolSize)
}

/**
 * Workaround to avoid:
 *
 * Error Reading Changelog File: Found 2 files with the path 'liquibase-changelog.xml'
 * Caused by: java.io.IOException: Found 2 files with the path 'liquibase-changelog.xml':
 *     - file:///home/benoit/Projects/xiangqi/xiangqi-db/build/resources/main/liquibase-changelog.xml
 *     - jar:file:/home/benoit/Projects/xiangqi/xiangqi-webapp/build/libs/xiangqi-webapp-1.0.jar!/liquibase-changelog.xml
 */
fun getScriptDslContext(dbConfig: DbConfig, maximumPoolSize: Int = 2): DSLContext {
    return getDslContext(
        dbConfig,
        maximumPoolSize,
        DirectoryResourceAccessor(File("webapp-dao-migration/src/main/resources"))
    )
}
