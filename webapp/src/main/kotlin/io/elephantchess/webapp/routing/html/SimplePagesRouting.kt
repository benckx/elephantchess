package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.services.KofiService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.renderTemplateHtmlToCall
import io.elephantchess.webapp.ops.requireIdentification
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.elephantchess.webapp.rendering.latestSupporterTagResolver
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val logger = logger {}

private val simplePublicPageMapping = mapOf(
    "/400" to "errors/400",
    "/401" to "errors/401",
    "/403" to "errors/403",
    "/404" to "errors/404",
    "/xiangqi-test-board" to "test_boards/xiangqi_test_board",
    "/manchu-test-board" to "test_boards/manchu_test_board",
    "/janggi-test-board" to "test_boards/janggi_test_board",
    "/database" to "database/database_search",
    "/database/search" to "database/database_search",
    "/browse/player-vs-player" to "browse_pvp_games",
    "/browse/player-vs-bot" to "browse_pvb_games",
    "/analysis" to "analysis_board",
    "/global" to "global",
    "/recovery" to "password_recovery1",
    "/recovery/finalize" to "password_recovery2",
    "/about" to "about/about",
    "/about/datasets" to "about/datasets",
    "/contact" to "contact_form",
    "/how-to-play-xiangqi" to "how_to_play_xiangqi",
    "/how-to-play-janggi" to "how_to_play_janggi",
    "/7k/game" to "seven_kingdoms/seven_kingdoms_game",
    "/7k/playground" to "seven_kingdoms/seven_kingdoms_playground",
    "/7k/about" to "seven_kingdoms/seven_kingdoms_about",
)

private val simplePublicPageMappingWithSupporterBanner = mapOf(
    "/" to "lobby",
)

private val publicPageRedirection = mapOf(
    "/7k" to "/7k/about",
    "/7k/rules" to "/7k/about",
    "/board" to "/xiangqi-test-board",
)

// available to guests and authenticated users
private val identificationRequiredPagesMapping = mapOf(
    "/userdata/games" to "userdata/my_games",
    "/userdata/botgames" to "userdata/my_bot_games",
    "/userdata/analysis" to "userdata/my_analysis",
    "/userdata/puzzles" to "userdata/my_played_puzzles",
    "/userdata/db-searches" to "userdata/my_db_searches",
)

internal fun Routing.simpleMappings() {
    val simplePageRenderer by koin<SimplePageRenderer>()
    val kofiService by koin<KofiService>()

    simplePublicPageMapping.forEach { (path, templateName) ->
        logger.info { "[public] mapping html file $templateName to path $path" }
        get(path) {
            call.respondHtml(simplePageRenderer.renderTemplate(templateName))
        }
    }
    publicPageRedirection.forEach { (fromPath, toPath) ->
        logger.info { "[public] mapping redirection from $fromPath to $toPath" }
        get(fromPath) {
            call.respondRedirect(toPath)
        }
    }
    identificationRequiredPagesMapping.forEach { (path, templateName) ->
        logger.info { "[identification] mapping html file $templateName to path $path" }
        get(path) {
            requireIdentification { _ ->
                simplePageRenderer.renderTemplateHtmlToCall(templateName)
            }
        }
    }
    simplePublicPageMappingWithSupporterBanner.forEach { (path, templateName) ->
        logger.info { "[public] mapping html file $templateName to path $path (with supporter banner)" }
        get(path) {
            val latestTipper = kofiService.fetchLatestSupporter()
            call.respondHtml(
                simplePageRenderer.renderTemplate(
                    templateName,
                    listOf(latestSupporterTagResolver(latestTipper)),
                ),
            )
        }
    }
}
