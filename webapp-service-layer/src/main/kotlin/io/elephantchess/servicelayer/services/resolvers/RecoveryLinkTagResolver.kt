package io.elephantchess.servicelayer.services.resolvers

import io.elephantchess.htmlrenderer.TagResolver
import java.net.URLEncoder

class RecoveryLinkTagResolver(
    private val webHost: String,
    private val address: String,
    private val code: String,
) : TagResolver {

    override val tagName = "recovery_link"

    override suspend fun resolveContent(): List<String> {
        val encodedAddress = URLEncoder.encode(address, "UTF-8")
        val url = "${webHost}/recovery/finalize?email=${encodedAddress}&code=$code"
        return listOf(makeAnchor(url))
    }

}
