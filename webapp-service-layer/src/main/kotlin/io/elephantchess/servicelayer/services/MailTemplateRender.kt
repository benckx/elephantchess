package io.elephantchess.servicelayer.services

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.htmlrenderer.TemplateRenderer

class MailTemplateRender(
    baseTagResolvers: List<TagResolver> = listOf(),
    disabledTemplates: List<String> = listOf(),
) : TemplateRenderer(baseTagResolvers, disabledTemplates)