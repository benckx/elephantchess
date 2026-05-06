package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.OpeningRepositoryCacheDaoService
import io.elephantchess.db.utils.nextMove
import io.elephantchess.servicelayer.dto.analysis.OpeningNextMovesRequest
import io.elephantchess.servicelayer.dto.analysis.OpeningNextMovesResponse

class OpeningService(private val openingRepositoryCacheDaoService: OpeningRepositoryCacheDaoService) {

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

}
