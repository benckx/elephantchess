package io.elephantchess.servicelayer.services.resolvers

import io.elephantchess.htmlrenderer.TagResolver

class EmailConfirmationLinkTagResolver(
    private val webHost: String,
    private val code: String,
) : TagResolver {

    override val tagName = "email_confirmation_link"

    override suspend fun resolveContent(): List<String> {
        val url = "${webHost}/email/confirm?code=$code"
        return listOf(makeAnchor(url))
    }

}
