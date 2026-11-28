package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME
import io.elephantchess.db.services.*
import io.elephantchess.model.AnalysisStatus.NOT_STARTED
import io.elephantchess.model.AnalysisStatus.STARTED
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType.*
import io.elephantchess.servicelayer.dto.admin.LatestMoveAnalysisByGameResponse
import io.elephantchess.servicelayer.dto.admin.MoveAnnotationFailuresResponse
import io.elephantchess.servicelayer.dto.admin.PreAnalysisStatusByGameTypeResponse
import io.elephantchess.servicelayer.dto.admin.PreAnalyzedReferenceGamesPerYearResponse
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.exceptions.PreConditionFailedException
import io.elephantchess.servicelayer.services.GameDataService
import io.elephantchess.servicelayer.services.GameDataService.Companion.MIN_MOVE_INDEX
import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton
class AdminAnalysisService(
    private val moveAnalysisDaoService: MoveAnalysisDaoService,
    private val referenceGameDaoService: ReferenceGameDaoService,
    private val playerVsBotGameDaoService: PlayerVsBotGameDaoService,
    private val playerVsPlayerGameDaoService: PlayerVsPlayerGameDaoService,
    private val gameDataService: GameDataService,
    private val thrownExceptionDaoService: ThrownExceptionDaoService,
) {

    suspend fun listMoveAnnotationFailures(): MoveAnnotationFailuresResponse =
        MoveAnnotationFailuresResponse(
            thrownExceptionDaoService.listMoveAnnotationFailures().map { record ->
                MoveAnnotationFailuresResponse.Entry(
                    gameId = record.gameId,
                    analysisStatus = moveAnalysisDaoService.fetchAnalysisStatus(record.gameId),
                    exceptionCount = record.exceptionCount,
                    lastExceptionTime = record.lastExceptionTime.toEpochMilliseconds(),
                )
            },
        )

    suspend fun cleanFailedMoveAnnotations(gameId: GameId): MoveAnnotationFailuresResponse {
        if (!thrownExceptionDaoService.hasMoveAnnotationFailure(gameId)) {
            throw NotFoundException("No move annotation failure recorded for $gameId")
        }
        val status = gameDataService.fetchAnalysisStatusOfGame(gameId)
        if (status == NOT_STARTED) {
            throw PreConditionFailedException("Analysis for $gameId is already not started")
        }
        if (status == STARTED) {
            throw PreConditionFailedException("Analysis for $gameId is running; wait for it to finish before cleaning")
        }
        gameDataService.resetAnalysis(gameId)
        return listMoveAnnotationFailures()
    }

    suspend fun listPreAnalysisStatusByGameType(): PreAnalysisStatusByGameTypeResponse {
        @Suppress("CollectionConcatenationToBuildCollection")
        val entries =
            referenceGameDaoService
                .countGamesByAnalysisStatus(MIN_MOVE_INDEX)
                .map { record ->
                    PreAnalysisStatusByGameTypeResponse.Entry(
                        gameType = DB,
                        status = record.value1(),
                        count = record.value2(),
                    )
                } +
                playerVsBotGameDaoService
                    .countGamesByAnalysisStatus(MIN_MOVE_INDEX)
                    .map { record ->
                        PreAnalysisStatusByGameTypeResponse.Entry(
                            gameType = PVB,
                            status = record.value1(),
                            count = record.value2(),
                        )
                    } +
                playerVsPlayerGameDaoService
                    .countGamesByAnalysisStatus(MIN_MOVE_INDEX)
                    .map { record ->
                        PreAnalysisStatusByGameTypeResponse.Entry(
                            gameType = PVP,
                            status = record.value1(),
                            count = record.value2(),
                        )
                    }

        return PreAnalysisStatusByGameTypeResponse(entries)
    }

    suspend fun listPreAnalyzedReferenceGamesPerYear(): PreAnalyzedReferenceGamesPerYearResponse {
        val entries =
            referenceGameDaoService
                .listPreAnalyzedGamesByYear()
                .map { record ->
                    PreAnalyzedReferenceGamesPerYearResponse.Entry(
                        year = record.get(REFERENCE_GAME.YEAR),
                        status = record.get(REFERENCE_GAME.ANALYSIS_STATUS),
                        count = record.get("count", Int::class.java),
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
                        analysisStatus = gameDataService.fetchAnalysisStatusOfGame(record.gameId),
                        analyzedFromBatch = record.analyzedFromBatch,
                    )
                }

        return LatestMoveAnalysisByGameResponse(entries)
    }
}
