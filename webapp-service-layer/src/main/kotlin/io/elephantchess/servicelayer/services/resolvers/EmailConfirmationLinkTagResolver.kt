package io.elephantchess.servicelayer.services.resolvers

import io.elephantchess.htmlrenderer.TagResolver
import java.net.URLEncoder

class EmailConfirmationLinkTagResolver(
    private val webHost: String,
    private val code: String,
) : TagResolver {

    override val tagName = "email_confirmation_link"

    override suspend fun resolveContent(): List<String> {
        val encodedCode = URLEncoder.encode(code, "UTF-8")
        val url = "${webHost}/email/confirm?code=$encodedCode"
        return listOf(makeAnchor(url))
    }

}
