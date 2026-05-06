package io.elephantchess.servicelayer.services.resolvers

import io.elephantchess.config.AppConfig
import io.elephantchess.htmlrenderer.TagResolver

class UnsubscribeTagResolver(
    appConfig: AppConfig,
    override val tagName: String,
    private val code: String
) :
    TagResolver {

    private val webHost = appConfig.webHost

    override suspend fun resolveContent(): List<String> {
        return listOf("${webHost}/unsubscribe?code=$code")
    }

}
