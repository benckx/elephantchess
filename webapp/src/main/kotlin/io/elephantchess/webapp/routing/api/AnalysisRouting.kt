package io.elephantchess.webapp.routing.api

import io.elephantchess.servicelayer.dto.analysis.*
import io.elephantchess.servicelayer.dto.engines.EngineRequest
import io.elephantchess.servicelayer.services.AnalysisService
import io.elephantchess.servicelayer.services.OpeningService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.requireAuthentication
import io.elephantchess.webapp.ops.requireAuthenticationWithBody
import io.elephantchess.webapp.ops.requireParam
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.commons.lang3.StringUtils.isNumeric

fun Route.analysisRoutes() {
    val analysisService by koin<AnalysisService>()
    val openingService by koin<OpeningService>()

    route("/api/analysis") {
        post("/openings/next-moves-info") {
            val request = call.receive<OpeningNextMovesRequest>()
            call.respond(openingService.fetchNextMovesData(request))
        }
        get("/get") {
            val version = call.parameters["version"]
            if (version != null && !isNumeric(version)) {
                call.response.status(BadRequest)
            } else {
                requireParam("analysisId") { analysisId ->
                    analysisService.findByIdAndVersion(analysisId, version?.toInt())
                }
            }
        }
        get("/engine-data") {
            requireParam("analysisId") { analysisId ->
                analysisService.fetchAnalysisEngineDataCache(analysisId)
            }
        }
        get("/list-user-analysis") {
            val continuation = call.parameters["continuation"]?.toLong()
            requireAuthentication { verifiedToken ->
                analysisService.listUserAnalysis(verifiedToken.userId, continuation)
            }
        }
        post("/save") {
            requireAuthenticationWithBody<SaveAnalysisRequest> { verifiedToken, request ->
                call.response.status(Created)
                analysisService.saveOrUpdateAnalysis(verifiedToken.userId, request)
            }
        }
        post("/delete") {
            requireAuthenticationWithBody<DeleteAnalysisRequest> { verifiedToken, request ->
                analysisService.deleteAnalysis(verifiedToken.userId, request)
            }
        }
        post("/rename") {
            requireAuthenticationWithBody<RenameAnalysisRequest> { verifiedToken, request ->
                analysisService.renameAnalysis(verifiedToken.userId, request)
            }
        }
        post("/update-start-fen") {
            requireAuthenticationWithBody<UpdateStartFenRequest> { verifiedToken, request ->
                analysisService.updateStartFen(verifiedToken.userId, request)
            }
        }
        post("/query-engine") {
            handleEngineRequest { request ->
                val analysisId = call.parameters["analysisId"]
                analysisService.queryEngine(request, analysisId)
            }
        }
    }
}

private suspend fun RoutingContext.handleEngineRequest(handler: suspend (EngineRequest) -> Any?) {
    val request = call.receive<EngineRequest>()
    val result = handler(request)
    if (result != null) {
        call.respond(result)
    } else {
        call.response.status(NoContent)
    }
}
