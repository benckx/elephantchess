package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.servicelayer.services.analytics.ARCHIVE_GUEST_AFTER_DAYS
import io.elephantchess.servicelayer.services.analytics.ARCHIVE_PAGE_VIEW_AFTER_DAYS
import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton(eager = true)
class PolicyPageRenderer(private val simplePageRenderer: SimplePageRenderer) {

    suspend fun renderPolicyPage(): String {
        val tocResolver = pageTocTagResolver("policy_toc", POLICY_TEMPLATE_PATH)

        return simplePageRenderer.renderTemplate(
            POLICY_TEMPLATE_NAME,
            listOf(
                tocResolver,
                SimpleValueTagResolver("page_views_retention_days", ARCHIVE_PAGE_VIEW_AFTER_DAYS.toString()),
                SimpleValueTagResolver("guest_retention_days", ARCHIVE_GUEST_AFTER_DAYS.toString()),
            ),
        )
    }

    private companion object {

        const val POLICY_TEMPLATE_NAME = "about/policy"
        const val POLICY_TEMPLATE_PATH = "/templates/$POLICY_TEMPLATE_NAME.html"
    }
}
