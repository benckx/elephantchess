package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.PageViewEvent
import io.elephantchess.db.services.PageViewEventDaoService
import io.elephantchess.db.utils.generateId
import io.elephantchess.servicelayer.model.AuthenticatedToken
import io.elephantchess.servicelayer.model.VerifiedToken
import io.github.oshai.kotlinlogging.KLogger
import kotlin.random.Random.Default.nextDouble

class PageViewEventService(
    private val pageViewDaoService: PageViewEventDaoService,
    private val userCache: UserCache,
    appConfig: AppConfig,
    private val logger: KLogger
) {

    private val webHost = appConfig.webHost
    private val webHostUnsecure = webHost.replace("https://", "http://")

    suspend fun processPageViewEvent(verifiedToken: VerifiedToken, currentPage: String?) {
        if (currentPage == null) {
            return
        }

        val eventPath = currentPage
            .removePrefix(webHost)
            .removePrefix(webHostUnsecure)
            .removePrefix("`") // why do I have entres like "/about?medium=footer`"?

        val record = PageViewEvent()
        record.eventId = generateId()
        record.userId = verifiedToken.userId
        record.eventPath = eventPath
        pageViewDaoService.save(record)

        logPingUserSession(verifiedToken, eventPath)
    }

    private suspend fun logPingUserSession(verifiedToken: VerifiedToken, eventPath: String) {
        if (logger.isInfoEnabled() && nextDouble() <= (1 / 20.0)) {
            val pingLoggingLine = when (verifiedToken) {
                is AuthenticatedToken -> {
                    if (verifiedToken.username() == null) {
                        val usernameFromDb = "[${userCache.fetchUsername(verifiedToken.userId)} (from db)]"
                        "page view received $verifiedToken $usernameFromDb for page $eventPath"
                    } else {
                        "page view received $verifiedToken for page $eventPath"
                    }
                }

                else -> "page view received $verifiedToken for page $eventPath"
            }

            logger.info { pingLoggingLine }
        }
    }

}
