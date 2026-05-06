package io.elephantchess.servicelayer.services.resolvers

import io.elephantchess.htmlrenderer.TagResolver

class GameLinkTagResolver(private val webHost: String, private val gameId: String) : TagResolver {

    override val tagName = "game_link"

    override suspend fun resolveContent(): List<String> {
        val url = "${webHost}/game?id=$gameId"
        return listOf(makeAnchor(url))
    }

}
