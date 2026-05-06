package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.htmlrenderer.TemplateRenderer

class WebTemplateRenderer(
    baseTagResolvers: List<TagResolver> = listOf(),
    disabledTemplates: List<String> = listOf(),
) : TemplateRenderer(baseTagResolvers, disabledTemplates)
