package io.elephantchess.servicelayer.services.resolvers

import io.elephantchess.config.AppConfig
import io.elephantchess.htmlrenderer.TagResolver

class ContactLinkTagResolver(appConfig: AppConfig) : TagResolver {

    override val tagName = "contact_link"
    private val webHost = appConfig.webHost

    override suspend fun resolveContent(): List<String> {
        val url = "${webHost}/about"
        return listOf(makeAnchor(url))
    }

}
