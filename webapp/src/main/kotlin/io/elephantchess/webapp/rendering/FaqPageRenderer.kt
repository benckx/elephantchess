package io.elephantchess.webapp.rendering

import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton(eager = true)
class FaqPageRenderer(private val simplePageRenderer: SimplePageRenderer) {

    suspend fun renderFaqPage(): String {
        val tocResolver = pageTocTagResolver("faq_toc", FAQ_TEMPLATE_PATH)

        return simplePageRenderer.renderTemplate(
            FAQ_TEMPLATE_NAME,
            listOf(tocResolver),
        )
    }

    private companion object {

        const val FAQ_TEMPLATE_NAME = "about/faq"
        const val FAQ_TEMPLATE_PATH = "/templates/$FAQ_TEMPLATE_NAME.html"
    }
}
