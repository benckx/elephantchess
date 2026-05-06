package io.elephantchess.servicelayer.services

import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.protocol.model.InfoLineResult
import io.elephantchess.engines.protocol.model.InfoLinesResult
import io.elephantchess.model.Engine
import io.elephantchess.model.Engine.PIKAFISH
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto.Companion.mapToInfoLineResultDto
import io.elephantchess.servicelayer.exceptions.InternalErrorException
import io.elephantchess.servicelayer.utils.modelToProcess
import io.elephantchess.servicelayer.utils.ops.safeQueryForDepth

class EngineService(
    private val enginesPool: EnginePool,
    private val engineCacheService: EngineCacheService,
) {

    /**
     * First try from cache, then query engine
     */
    suspend fun principalVariation(fen: String, engine: Engine, depth: Int): InfoLineResultDto? {
        suspend fun queryEngine(): InfoLineResult? {
            return queryEngine(fen, engine, depth).deepestResult()
        }

        val infoLineResult = when (engine) {
            PIKAFISH -> {
                when (val infoLineResult = engineCacheService.get(fen, depth)) {
                    null -> queryEngine()
                    else -> infoLineResult
                }
            }

            else -> queryEngine()
        }

        return if (infoLineResult != null) {
            mapToInfoLineResultDto(fen, infoLineResult)
        } else {
            null
        }
    }

    private suspend fun queryEngine(fen: String, engine: Engine, depth: Int): InfoLinesResult {
        val result =
            enginesPool.safeQueryForDepth(
                fen = fen,
                engineId = modelToProcess(engine),
                depth = depth,
                timeout = ENGINE_TIME_OUT
            )

        if (result == null) {
            throw InternalErrorException("No result from engine")
        } else {
            return result
        }
    }

    private companion object {

        const val ENGINE_TIME_OUT = 15_000L

    }

}
