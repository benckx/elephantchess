package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.model.Engine
import io.elephantchess.servicelayer.services.KofiService
import io.elephantchess.servicelayer.services.PlayerVsBotGameService
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.elephantchess.xiangqi.Color

class GamePageRenderer(
    private val htmlRenderer: HtmlRenderer,
    private val kofiService: KofiService,
    private val pvpGameService: PlayerVsPlayerGameService,
    private val pvbGameService: PlayerVsBotGameService
) {

    suspend fun renderPvpGamePage(gameId: String): String {
        val title = fetchPvpTitle(gameId)

        return htmlRenderer.renderHtml(
            templatePath = "/templates/player_vs_player_game.html",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("page_title", title)
            ),
            canonicalPath = "/game?id=$gameId"
        )
    }

    suspend fun renderPvbGamePage(gameId: String): String {
        val title = fetchPvbTitle(gameId)
        val latestSupporter = kofiService.fetchLatestSupporter()

        return htmlRenderer.renderHtml(
            templatePath = "/templates/player_vs_bot.html",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("page_title", title),
                latestSupporterTagResolver(latestSupporter)
            ),
            canonicalPath = "/playbot?id=$gameId"
        )
    }

    private suspend fun fetchPvpTitle(gameId: String): String {
        val title = gameId.let { id ->
            runCatching {
                val game = pvpGameService.fetchGame(id)
                formatPvpPageTitle(game.inviterUsername, game.inviteeUsername, game.inviterColor)
            }.getOrDefault(DEFAULT_PVP_TITLE)
        }

        return escapeHtml(title)
    }

    private suspend fun fetchPvbTitle(gameId: String): String {
        val title = gameId.let { id ->
            runCatching {
                val game = pvbGameService.fetchGameData(id)
                pvbPageTitle(game.username, game.userColor, game.engine, game.depth)
            }.getOrDefault(DEFAULT_PVB_TITLE)
        }

        return escapeHtml(title)
    }

    companion object {
        internal const val DEFAULT_PVP_TITLE = "Game"
        internal const val DEFAULT_PVB_TITLE = "Play vs. Bot"

        internal fun formatPvpPageTitle(inviterUsername: String, inviteeUsername: String?, inviterColor: Color?): String {
            if (inviterColor == null) {
                return "new game by $inviterUsername"
            }

            val invitee = inviteeUsername ?: "opponent"
            return when (inviterColor) {
                Color.RED -> "$inviterUsername vs $invitee"
                Color.BLACK -> "$invitee vs $inviterUsername"
            }
        }

        internal fun pvbPageTitle(username: String?, userColor: Color, engine: Engine, depth: Int): String {
            val humanPlayer = username ?: "anonymous"
            val botPlayer = "${formatEngineName(engine)} ($depth)"

            return when (userColor) {
                Color.RED -> "$humanPlayer vs. $botPlayer"
                Color.BLACK -> "$botPlayer vs. $humanPlayer"
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
