package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.model.UserSessionRecord
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.servicelayer.clients.ApiLayerClient
import io.elephantchess.servicelayer.utils.extractAddress
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.validator.routines.InetAddressValidator

class UserSessionService(
    appConfig: AppConfig,
    private val apiLayerClient: ApiLayerClient,
    private val userSessionDaoService: UserSessionDaoService,
    private val logger: KLogger,
) {

    private val parseUserAgent = appConfig.parseUserAgent
    private val sessionScope by lazy { CoroutineScope(Dispatchers.Default) }

    fun handleUserSession(userId: String?, remoteAddress: String, headers: Map<String, List<String>>) {
        sessionScope.launch {
            persistUserSessionSync(userId, remoteAddress, headers)
        }
    }

    private suspend fun persistUserSessionSync(
        userId: String?,
        remoteAddress: String,
        headers: Map<String, List<String>>
    ) {
        fun cleanAddress(address: String): String? {
            val validator = InetAddressValidator.getInstance()

            return address
                .split(",")
                .asSequence()
                .map { it.trim() }
                .filterNot { it == "127.0.0.1" }
                .filterNot { it.startsWith("10.") }
                .filter { it.isNotBlank() }
                .filter { validator.isValid(it) }
                .firstOrNull()
        }

        val userAgent1 = headers["User-Agent"]?.firstOrNull()
        val userAgent2 = headers["user-agent"]?.firstOrNull()
        val userAgent = userAgent1 ?: userAgent2
        val address = extractAddress(remoteAddress, headers)?.let { cleanAddress(it) }

        if (userAgent != null && address != null) {
            try {
                val response = findUserAgentInfo(userAgent)
                if (response != null) {
                    val record = UserSessionRecord(
                        userId = userId,
                        remoteAddress = address,
                        userAgent = userAgent,
                        operatingSystemName = response.osName,
                        agentName = response.agentName
                    )

                    userSessionDaoService.createOrUpdate(record)
                }
            } catch (e: Exception) {
                logger.warn { "error parsing User-Agent $userAgent -> $e" }
            }
        }
    }

    /**
     * If not in DB, then call API Layer to parse User-Agent
     */
    private suspend fun findUserAgentInfo(userAgent: String): UserAgentInfo? {
        val fromDb =
            userSessionDaoService
                .findByUserAgent(userAgent)
                .map { record ->
                    UserAgentInfo(record.operatingSystemName, record.agentName)
                }
                .distinct()

        when (fromDb.size) {
            0 -> {
                if (parseUserAgent) {
                    logger.debug { "no match in db for $userAgent, will fetch from API" }
                    apiLayerClient.parseUserAgent(userAgent)
                        ?.let { response ->
                            val osName = response.os.name
                            val agentName = response.browser.name
                            if (osName != null && agentName != null) {
                                return UserAgentInfo(osName, agentName)
                            }
                        }
                }
            }

            1 -> return fromDb.first()
            else -> {
                logger.warn { "multiple DB records matching for User-Agent $userAgent" }
                return fromDb.first()
            }
        }

        return null
    }

    private companion object {

        data class UserAgentInfo(val osName: String, val agentName: String)

    }

}
