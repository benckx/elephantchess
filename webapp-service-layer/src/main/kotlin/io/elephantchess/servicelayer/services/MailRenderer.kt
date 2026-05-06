package io.elephantchess.servicelayer.services

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.utils.ResourceUtils.resourceAsString

class MailRenderer(private val mailTemplateRenderer: MailTemplateRender) {

    suspend fun renderEmail(templateName: String, specificTagResolvers: List<TagResolver>): String =
        render("mail_templates", templateName, specificTagResolvers)

    suspend fun renderNewsLetter(templateName: String, specificTagResolvers: List<TagResolver>): String =
        render("newsletters", templateName, specificTagResolvers)

    private suspend fun render(path: String, templateName: String, specificTagResolvers: List<TagResolver>): String {
        return resourceAsString("/$path/$templateName.html")
            ?.let { html -> mailTemplateRenderer.render(html, specificTagResolvers) }
            ?: throw IllegalArgumentException("Template $path/$templateName not found")
    }

}
