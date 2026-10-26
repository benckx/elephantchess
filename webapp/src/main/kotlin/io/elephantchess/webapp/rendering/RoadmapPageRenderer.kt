package io.elephantchess.webapp.rendering

import io.elephantchess.utils.di.KoinSingleton
import io.elephantchess.webapp.rendering.resolvers.pageTocTagResolver

@KoinSingleton(eager = true)
class RoadmapPageRenderer(private val simplePageRenderer: SimplePageRenderer) {

    suspend fun renderRoadmapPage(): String {
        val tocResolver = pageTocTagResolver("roadmap_toc", ROADMAP_TEMPLATE_PATH)

        return simplePageRenderer.renderTemplate(
            templateName = ROADMAP_TEMPLATE_NAME,
            specificTagResolvers = listOf(tocResolver),
        )
    }

    private companion object {

        const val ROADMAP_TEMPLATE_NAME = "about/roadmap"
        const val ROADMAP_TEMPLATE_PATH = "/templates/$ROADMAP_TEMPLATE_NAME.html"
    }
}
