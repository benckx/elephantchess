package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME
import io.elephantchess.db.services.MoveAnalysisDaoService
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.servicelayer.dto.admin.LatestMoveAnalysisByGameResponse
import io.elephantchess.servicelayer.dto.admin.PreAnalyzedReferenceGamesPerYearResponse
import io.elephantchess.servicelayer.services.GameDataService

class AdminAnalysisService(
    private val moveAnalysisDaoService: MoveAnalysisDaoService,
    private val referenceGameDaoService: ReferenceGameDaoService,
    private val gameDataService: GameDataService
) {

    suspend fun listPreAnalyzedReferenceGamesPerYear(): PreAnalyzedReferenceGamesPerYearResponse {
        val entries =
            referenceGameDaoService
                .listPreAnalyzedGamesByYear()
                .map { record ->
                    PreAnalyzedReferenceGamesPerYearResponse.Entry(
                        year = record.get(REFERENCE_GAME.YEAR),
                        status = record.get(REFERENCE_GAME.ANALYSIS_STATUS),
                        count = record.get("count", Int::class.java)
                    )
                }

        return PreAnalyzedReferenceGamesPerYearResponse(entries)
    }

    suspend fun listLatestMoveAnalysisByGame(): LatestMoveAnalysisByGameResponse {
        val entries =
            moveAnalysisDaoService
                .listLatestMoveAnalysisData(60)
                .map { record ->
                    LatestMoveAnalysisByGameResponse.Entry(
                        gameId = record.gameId,
                        first = record.first.toEpochMilliseconds(),
                        last = record.last.toEpochMilliseconds(),
                        totalAnalyzedMoves = record.totalAnalyzedMoves,
                        analysisStatus = gameDataService.fetchAnalysisStatusOfGame(record.gameId)
                    )
                }

        return LatestMoveAnalysisByGameResponse(entries)
    }

}
