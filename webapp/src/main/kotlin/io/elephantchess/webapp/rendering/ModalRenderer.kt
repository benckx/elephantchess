package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.github.oshai.kotlinlogging.KLogger
import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration.Companion.hours

class ModalRenderer(
    private val htmlRenderer: HtmlRenderer,
    private val logger: KLogger,
) {

    private val defaultExpiration = 1.hours

    private val modalCache =
        Cache
            .Builder<String, String>()
            .expireAfterWrite(defaultExpiration)
            .build()

    suspend fun renderModal(modalName: String): String? {
        val result = modalCache.get(modalName) {
            htmlRenderer.renderHtml(
                templatePath = "/modals/$modalName.html",
                specificTagResolvers = emptyList(),
                skipDocType = true
            )
        }

        return result.ifEmpty {
            modalCache.invalidate(modalName)
            logger.warn { "modal html file not found for '$modalName' " }
            null
        }
    }

}
