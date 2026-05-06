package io.elephantchess.servicelayer.services.resolvers

import io.elephantchess.htmlrenderer.ResourceTagResolver

class MailFragmentResolver(override val tagName: String) : ResourceTagResolver(tagName) {

    override fun resolvePath(tagName: String): String = "/mail_fragments/$tagName.html"

}
