package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.EngineCacheDaoService
import io.elephantchess.engines.protocol.model.InfoLineResult
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount

class EngineCacheService(private val engineCacheDaoService: EngineCacheDaoService) {

    suspend fun get(fen: String, depth: Int): InfoLineResult? {
        val fenKey = resetFullMoveCount(fen)
        return engineCacheDaoService.fetchInfoLine(fenKey, depth)
    }

    suspend fun get(fen: String): InfoLineResult? {
        val fenKey = resetFullMoveCount(fen)
        return engineCacheDaoService.fetchInfoLine(fenKey)
    }

}
