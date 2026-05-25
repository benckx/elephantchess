package io.elephantchess.servicelayer.utils

import io.elephantchess.engines.EnginePool
import io.elephantchess.servicelayer.batch.definitions.BatchesScheduler
import io.elephantchess.servicelayer.services.PlayerVsBotGameService
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.elephantchess.servicelayer.services.PuzzleCache
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.services.sitemap.SiteMapService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jooq.DSLContext

private val logger = KotlinLogging.logger {}

/**
 * Handles graceful shutdown of application resources
 */
class ShutdownHandler(
    private val dslContext: DSLContext,
    private val playerVsPlayerGameService: PlayerVsPlayerGameService,
    private val playerVsBotGameService: PlayerVsBotGameService,
    private val userService: UserService,
    private val puzzleCache: PuzzleCache,
    private val enginePool: EnginePool,
    private val batchesScheduler: BatchesScheduler,
    private val siteMapService: SiteMapService,
    private val coroutineScope: CoroutineScope,
) {

    fun shutdown() {
        logger.info { "starting shutdown sequence" }

        try {
            // cancel PlayerVsPlayerGameService
            logger.info { "cancelling PlayerVsPlayerGameService..." }
            playerVsPlayerGameService.cancel()
            logger.info { "PlayerVsPlayerGameService cancelled" }
        } catch (e: Exception) {
            logger.error(e) { "error cancelling batch PlayerVsPlayerGameService" }
        }

        try {
            // cancel PlayerVsBotGameService
            logger.info { "cancelling PlayerVsBotGameService..." }
            playerVsBotGameService.cancel()
            logger.info { "PlayerVsBotGameService cancelled" }
        } catch (e: Exception) {
            logger.error(e) { "error cancelling batch PlayerVsBotGameService" }
        }

        try {
            // cancel UserService
            logger.info { "cancelling UserService..." }
            userService.cancel()
        } catch (e: Exception) {
            logger.error(e) { "error cancelling batch UserService" }
        }

        try {
            // cancel PuzzleCache
            logger.info { "cancelling PuzzleCache..." }
            puzzleCache.cancel()
        } catch (e: Exception) {
            logger.error(e) { "error cancelling batch PuzzleCache" }
        }

        try {
            // cancel batch scheduler jobs
            logger.info { "cancelling batch scheduler jobs..." }
            batchesScheduler.cancel()
        } catch (e: Exception) {
            logger.error(e) { "error cancelling batch scheduler" }
        }

        try {
            // cancel site map job
            logger.info { "cancelling site map service..." }
            siteMapService.cancel()
        } catch (e: Exception) {
            logger.error(e) { "error cancelling site map service" }
        }

        try {
            // close engine pool
            logger.info { "closing engine pool..." }
            enginePool.close()
        } catch (e: Exception) {
            logger.error(e) { "error closing engine pool" }
        }

        try {
            // close database connection pool
            logger.info { "closing database connection pool..." }
            val connectionFactory = dslContext.configuration().connectionFactory()
            // use reflection to avoid compile-time dependency on R2DBC
            val disposeMethod = connectionFactory.javaClass.getMethod("dispose")
            disposeMethod.invoke(connectionFactory)
            logger.info { "database connection pool closed" }
        } catch (e: NoSuchMethodException) {
            logger.warn { "connection factory does not have a dispose method: ${e.message}" }
        } catch (e: Exception) {
            logger.error(e) { "error closing database connection pool" }
        }

        try {
            // cancel coroutine scope (LAST - after all services using it are stopped)
            logger.info { "cancelling coroutine scope..." }
            coroutineScope.cancel()
            logger.info { "coroutine scope cancelled" }
        } catch (e: Exception) {
            logger.error(e) { "error cancelling coroutine scope" }
        }

        logger.info { "shutdown sequence complete" }
    }

}
