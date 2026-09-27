package io.elephantchess.scripts.users

import io.elephantchess.config.ArgConfig.Companion.parseArgs
import io.elephantchess.config.loadAppConfig
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.scripts.utils.getScriptDslContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

private const val HANDLE = "benckx"
private const val EMAIL = "benoit.vleminckx@gmail.com"

// to check whether we can drop .fixed() safely
fun main(args: Array<String>) {
    val argsConfig = parseArgs(args)
    logger.info { "args: ${args.joinToString(" ")}" }
    logger.info { "starting with $argsConfig" }

    val appConfig = loadAppConfig(argsConfig)
    val dslContext = getScriptDslContext(appConfig, maximumPoolSize = 2)

    runBlocking {
        val updated = dslContext.transactionCoroutine { cfg ->
            DSL.using(cfg)
                .update(USER)
                .set(USER.EMAIL, EMAIL)
                .set(USER.LAST_ONLINE, Clock.System.now())
                .where(USER.HANDLE.eq(HANDLE))
                .awaitExecute()
        }

        logger.info { "rows updated for handle '$HANDLE': $updated" }
    }
}
