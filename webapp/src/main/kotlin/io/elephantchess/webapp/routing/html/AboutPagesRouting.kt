package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.ChangelogPageRenderer
import io.elephantchess.webapp.rendering.FaqPageRenderer
import io.elephantchess.webapp.rendering.PolicyPageRenderer
import io.elephantchess.webapp.rendering.RoadmapPageRenderer
import io.ktor.server.routing.*

internal fun Route.aboutPagesRouting() {
    val changelogPageRenderer by koin<ChangelogPageRenderer>()
    val faqPageRenderer by koin<FaqPageRenderer>()
    val policyPageRenderer by koin<PolicyPageRenderer>()
    val roadmapPageRenderer by koin<RoadmapPageRenderer>()

    get("/about/roadmap") {
        call.respondHtml(roadmapPageRenderer.renderRoadmapPage())
    }
    get("/about/faq") {
        call.respondHtml(faqPageRenderer.renderFaqPage())
    }
    get("/about/policy") {
        call.respondHtml(policyPageRenderer.renderPolicyPage())
    }
    get("/about/changelog") {
        call.respondHtml(changelogPageRenderer.renderChangelogPage())
    }
}
