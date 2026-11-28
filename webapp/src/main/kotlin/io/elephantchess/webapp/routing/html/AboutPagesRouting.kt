package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.*
import io.ktor.server.routing.*

internal fun Route.aboutPagesRouting() {
    val simplePageRenderer by koin<SimplePageRenderer>()
    val roadmapPageRenderer by koin<RoadmapPageRenderer>()
    val faqPageRenderer by koin<FaqPageRenderer>()
    val policyPageRenderer by koin<PolicyPageRenderer>()
    val changelogPageRenderer by koin<ChangelogPageRenderer>()

    route("/about") {
        get {
            call.respondHtml(simplePageRenderer.renderTemplate("about/about"))
        }
        get("/datasets") {
            call.respondHtml(simplePageRenderer.renderTemplate("about/datasets"))
        }
        get("/roadmap") {
            call.respondHtml(roadmapPageRenderer.renderRoadmapPage())
        }
        get("/faq") {
            call.respondHtml(faqPageRenderer.renderFaqPage())
        }
        get("/policy") {
            call.respondHtml(policyPageRenderer.renderPolicyPage())
        }
        get("/changelog") {
            call.respondHtml(changelogPageRenderer.renderChangelogPage())
        }
    }
}
