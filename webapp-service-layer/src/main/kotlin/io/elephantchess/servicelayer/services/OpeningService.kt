package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.OpeningRepositoryCacheDaoService
import io.elephantchess.db.services.OpeningRepositoryReferencePlayerCacheDaoService
import io.elephantchess.db.utils.nextMove
import io.elephantchess.servicelayer.dto.analysis.OpeningNextMovesRequest
import io.elephantchess.servicelayer.dto.analysis.OpeningNextMovesResponse
import io.elephantchess.servicelayer.dto.analysis.OpeningReferencePlayerNextMovesRequest
import io.elephantchess.xiangqi.Color

class OpeningService(
    private val openingRepositoryCacheDaoService: OpeningRepositoryCacheDaoService,
    private val openingRepositoryReferencePlayerCacheDaoService: OpeningRepositoryReferencePlayerCacheDaoService,
) {

    suspend fun fetchNextMovesData(request: OpeningNextMovesRequest): OpeningNextMovesResponse {
        val entries =
            openingRepositoryCacheDaoService
                .fetchNextMovesData(request.moves)
                .map { record ->
                    OpeningNextMovesResponse.Entry(
                        nextMove = record.nextMove(),
                        occurrences = record.occurrences,
                        redWinsRate = record.outcomeRedWins.toFloat() / record.occurrences.toFloat(),
                        blackWinsRate = record.outcomeBlackWins.toFloat() / record.occurrences.toFloat(),
                    )
                }
                .sortedByDescending { entry ->
                    entry.occurrences
                }

        return OpeningNextMovesResponse(entries, request.moves)
    }

    /**
     * Fetch the opening repertoire of a single reference player. When [OpeningReferencePlayerNextMovesRequest.color]
     * is `null` ("all"), the per-color entries are aggregated by next move.
     */
    suspend fun fetchReferencePlayerNextMovesData(
        request: OpeningReferencePlayerNextMovesRequest,
    ): OpeningNextMovesResponse {
        val color = request.color?.let { Color.valueOf(it.uppercase()) }

        val entries =
            openingRepositoryReferencePlayerCacheDaoService
                .fetchNextMovesData(request.playerId, color, request.moves)
                .groupBy { record -> record.nextMove() }
                .map { (nextMove, records) ->
                    val occurrences = records.sumOf { it.occurrences }
                    val redWins = records.sumOf { it.outcomeRedWins }
                    val blackWins = records.sumOf { it.outcomeBlackWins }
                    OpeningNextMovesResponse.Entry(
                        nextMove = nextMove,
                        occurrences = occurrences,
                        redWinsRate = redWins.toFloat() / occurrences.toFloat(),
                        blackWinsRate = blackWins.toFloat() / occurrences.toFloat(),
                    )
                }
                .sortedByDescending { entry ->
                    entry.occurrences
                }

        return OpeningNextMovesResponse(entries, request.moves)
    }

}
