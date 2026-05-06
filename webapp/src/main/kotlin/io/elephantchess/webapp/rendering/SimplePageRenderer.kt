package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.TagResolver
import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration.Companion.minutes

class SimplePageRenderer(private val htmlRenderer: HtmlRenderer) {

    private val defaultExpiration = 10.minutes

    private val htmlCache =
        Cache
            .Builder<String, String>()
            .expireAfterWrite(defaultExpiration)
            .build()

    suspend fun renderTemplate(
        templateName: String,
        specificTagResolvers: List<TagResolver> = listOf()
    ): String =
        htmlCache.get(templateName) { renderTemplateNoCache(templateName, specificTagResolvers) }

    suspend fun renderTemplateNoCache(
        templateName: String,
        specificTagResolvers: List<TagResolver> = listOf()
    ): String =
        htmlRenderer.renderHtml("/templates/$templateName.html", specificTagResolvers)

}
