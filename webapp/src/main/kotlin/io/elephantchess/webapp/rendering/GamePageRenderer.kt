package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.model.Engine
import io.elephantchess.servicelayer.dto.kofi.LatestSupporter
import io.elephantchess.servicelayer.services.PlayerVsBotGameService
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.elephantchess.xiangqi.Color

class GamePageRenderer(
    private val htmlRenderer: HtmlRenderer,
    private val pvpGameService: PlayerVsPlayerGameService,
    private val pvbGameService: PlayerVsBotGameService
) {

    suspend fun renderPvpGamePage(gameId: String?): String {
        val title = gameId?.let { id ->
            runCatching {
                val game = pvpGameService.fetchGame(id)
                pvpPageTitle(game.inviterUsername, game.inviteeUsername)
            }.getOrDefault(DEFAULT_PVP_TITLE)
        } ?: DEFAULT_PVP_TITLE

        return htmlRenderer.renderHtml(
            templatePath = "/templates/player_vs_player_game.html",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("page_title", escapeHtml(title))
            ),
            canonicalPath = gameId?.let { "/game?id=$it" } ?: "/game"
        )
    }

    suspend fun renderPvbGamePage(gameId: String?, latestSupporter: LatestSupporter?): String {
        val title = gameId?.let { id ->
            runCatching {
                val game = pvbGameService.fetchGameData(id)
                pvbPageTitle(game.username, game.userColor, game.engine, game.depth)
            }.getOrDefault(DEFAULT_PVB_TITLE)
        } ?: DEFAULT_PVB_TITLE

        return htmlRenderer.renderHtml(
            templatePath = "/templates/player_vs_bot.html",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("page_title", escapeHtml(title)),
                latestSupporterTagResolver(latestSupporter)
            ),
            canonicalPath = gameId?.let { "/playbot?id=$it" } ?: "/playbot"
        )
    }

    companion object {
        internal const val DEFAULT_PVP_TITLE = "Game"
        internal const val DEFAULT_PVB_TITLE = "Play vs. Bot"

        internal fun pvpPageTitle(inviterUsername: String, inviteeUsername: String?): String {
            return "$inviterUsername vs ${inviteeUsername ?: "opponent"}"
        }

        internal fun pvbPageTitle(username: String?, userColor: Color, engine: Engine, depth: Int): String {
            val humanPlayer = username ?: "anonymous"
            val botPlayer = "${formatEngineName(engine)} ($depth)"

            return when (userColor) {
                Color.RED -> "$humanPlayer vs $botPlayer"
                Color.BLACK -> "$botPlayer vs $humanPlayer"
            }
        }

        private fun formatEngineName(engine: Engine): String {
            return when (engine) {
                Engine.PIKAFISH -> "Pikafish"
                Engine.FAIRYSTOCKFISH -> "Fairy Stockfish"
            }
        }
    }

}
