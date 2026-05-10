package io.elephantchess.webapp.routing.api

import io.elephantchess.model.PuzzleCategory
import io.elephantchess.servicelayer.dto.puzzles.PuzzleBestMoveRequest
import io.elephantchess.servicelayer.dto.puzzles.PuzzleOutcomeRequest
import io.elephantchess.servicelayer.dto.puzzles.PuzzleVoteRequest
import io.elephantchess.servicelayer.dto.puzzles.PuzzlesOriginalGameMetadataRequest
import io.elephantchess.servicelayer.services.GlobalAnalyticsService
import io.elephantchess.servicelayer.services.PuzzleService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.requireAuthentication
import io.elephantchess.webapp.ops.requireIdentification
import io.elephantchess.webapp.ops.requireParam
import io.elephantchess.webapp.ops.requireUserId
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val logger = KotlinLogging.logger {}
private val puzzleService by koin<PuzzleService>()
private val globalAnalyticsService by koin<GlobalAnalyticsService>()

fun Route.puzzleRoutes() {
    route("/api/puzzle") {
        post("/original-games-metadata") {
            val request = call.receive<PuzzlesOriginalGameMetadataRequest>()
            call.respond(puzzleService.fetchPuzzlesOriginalGameMetadata(request))
        }
        get("/get") {
            requireParam("id") { puzzleId ->
                val categories = extractCategories(call)
                puzzleService.fetchById(puzzleId, categories)
            }
        }
        get("/current") {
            requireIdentification { verifiedToken ->
                val categories = extractCategories(call)
                logger.debug { "fetch current puzzle for $verifiedToken" }
                puzzleService.fetchCurrent(verifiedToken.userId, categories)
            }
        }
        get("/next") {
            requireIdentification { verifiedToken ->
                // TODO: this should be merge with the "outcome" call, otherwise we can use this to skip puzzle
                val categories = extractCategories(call)
                logger.debug { "fetch next puzzle for $verifiedToken" }
                puzzleService.fetchNextPuzzleForUser(verifiedToken.userId, categories)
            }
        }
        post("/outcome") {
            val request = call.receive<PuzzleOutcomeRequest>()
            requireIdentification { verifiedToken ->
                logger.debug { "processing outcome for $verifiedToken -> $request" }
                puzzleService.processOutcome(request, verifiedToken.userId)
            }
        }
        post("/vote") {
            val request = call.receive<PuzzleVoteRequest>()
            requireAuthentication { verifiedToken ->
                puzzleService.persistVote(request, verifiedToken.userId)
            }
        }
        get("list-played-puzzles") {
            val continuation = call.parameters["continuation"]?.toLong()
            requireIdentification { verifiedToken ->
                puzzleService.listPlayedPuzzles(verifiedToken.userId, continuation)
            }
        }
        post("best-move") {
            val request = call.receive<PuzzleBestMoveRequest>()
            call.respond(puzzleService.bestMove(request))
        }
        get("/stats/global") {
            call.respond(globalAnalyticsService.fetchGlobalPuzzleStats())
        }
    }
    get("/api/user/info/puzzles/rating/{userId}") {
        requireUserId { puzzleService.fetchPuzzleRating(it) }
    }
}

private fun extractCategories(call: ApplicationCall): List<PuzzleCategory> {
    val paramCategories = call.request.queryParameters.getAll("category") ?: emptyList()
    return PuzzleCategory.parseList(paramCategories)
}
