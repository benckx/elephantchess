package io.elephantchess.servicelayer.batch

import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.utils.di.KoinSingleton
import io.github.oshai.kotlinlogging.KLogger

/**
 * Pairs pending games whose inviters are both online. Runs on a single pod so that
 * concurrent passes on several pods do not try to join or cancel the same games.
 */
@KoinSingleton
class DynamicMatchingBatch(
    private val pvpGameService: PlayerVsPlayerGameService,
    private val userService: UserService,
    override val logger: KLogger,
) : SinglePodBatch {

    override suspend fun run() {
        val onlineUserIds = userService.onlineUserIds()
        if (onlineUserIds.size >= 2) {
            pvpGameService.attemptToMatchExistingGames(onlineUserIds)
        }
    }
}
