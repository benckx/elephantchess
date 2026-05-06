package io.elephantchess.servicelayer.services.resolvers

import io.elephantchess.config.AppConfig
import io.elephantchess.htmlrenderer.TagResolver

class UpdateMailSettingsTagResolver(appConfig: AppConfig) : TagResolver {

    override val tagName = "update_mail_settings"
    private val webHost = appConfig.webHost

    override suspend fun resolveContent(): List<String> {
        val url = "${webHost}/user/settings"
        return listOf("You can update your email notifications settings at ${makeAnchor(url)}")
    }

}
