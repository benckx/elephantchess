package io.elephantchess.servicelayer.batch

import io.elephantchess.db.dao.codegen.tables.pojos.Game
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.utils.timeControl
import io.elephantchess.servicelayer.batch.definitions.ShardedBatch
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.github.oshai.kotlinlogging.KLogger

class FlagGamesBatch(
    private val pvpGameService: PlayerVsPlayerGameService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    override val logger: KLogger,
) : ShardedBatch<Game>() {

    override fun shardKey(element: Game): String =
        element.id

    override suspend fun fetchAll() =
        pvpGameDaoService.listPotentiallyFlaggedGames()

    override suspend fun process(element: Game) =
        pvpGameService.flagIfNeeded(element.id, element.timeControl()!!)

}
