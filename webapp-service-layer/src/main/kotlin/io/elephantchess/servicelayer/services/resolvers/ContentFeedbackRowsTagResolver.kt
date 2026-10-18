package io.elephantchess.servicelayer.services.resolvers

import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.servicelayer.services.ContentFeedbackEntry
import kotlinx.html.td
import kotlinx.html.tr

class ContentFeedbackRowsTagResolver(
    contentFeedbackEntries: List<ContentFeedbackEntry>,
) : TagResolver by KtorHtmlBuilderTagResolver("rows", {
    contentFeedbackEntries.forEach { entry ->
        tr {
            td { +"${entry.pageId} / ${entry.sectionId}" }
            td { +entry.userId }
            td { +(if (entry.upVoted) "up" else "down") }
            td { +(entry.feedback ?: "-") }
        }
    }
})
