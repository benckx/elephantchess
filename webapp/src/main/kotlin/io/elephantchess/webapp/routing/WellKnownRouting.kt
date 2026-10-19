package io.elephantchess.webapp.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Serves well-known resources under `/.well-known/`.
 *
 * - `/.well-known/traffic-advice`: consumed by Chrome's "Private Prefetch Proxy". Returning an
 *   explicit opt-in lets the proxy prefetch our pages, and stops the proxy from re-requesting it
 *   constantly (which otherwise shows up as UNMAPPED URI 404 warnings in the logs).
 *   See https://developer.chrome.com/docs/privacy-sandbox/private-prefetch-proxy/
 *
 * - `/.well-known/security.txt`: RFC 9116 standard location telling security researchers how to
 *   report vulnerabilities. We have no public security mailbox, so we point at the contact form.
 *   See https://www.rfc-editor.org/rfc/rfc9116
 */
fun Application.wellKnownRoutingModule() {
    // application/trafficadvice+json — opt in to prefetching for the prefetch proxy user agent.
    val trafficAdvice = """
        [
          {
            "user_agent": "prefetch-proxy",
            "fraction": 1.0
          }
        ]
    """.trimIndent()

    val trafficAdviceContentType = ContentType("application", "trafficadvice+json")

    routing {
        route("/.well-known/traffic-advice") {
            get {
                call.respondText(
                    text = trafficAdvice,
                    contentType = trafficAdviceContentType,
                    status = HttpStatusCode.OK,
                )
            }
            head {
                call.response.status(HttpStatusCode.OK)
                call.response.header(HttpHeaders.ContentType, trafficAdviceContentType.toString())
                call.response.header(HttpHeaders.ContentLength, trafficAdvice.length.toString())
            }
        }

        route("/.well-known/security.txt") {
            get {
                call.respondText(
                    text = buildSecurityTxt(),
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.OK,
                )
            }
            head {
                val body = buildSecurityTxt()
                call.response.status(HttpStatusCode.OK)
                call.response.header(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                call.response.header(HttpHeaders.ContentLength, body.length.toString())
            }
        }
    }
}

/**
 * Builds an RFC 9116 compliant security.txt. The `Expires` field is required and must be in the
 * future, so it is computed dynamically (one year from now) to avoid it ever becoming stale.
 */
private fun buildSecurityTxt(): String {
    val expires = ZonedDateTime.now(ZoneOffset.UTC)
        .plusYears(1)
        .format(DateTimeFormatter.ISO_INSTANT)

    return """
        Contact: mailto:info@elephantchess.io
        Expires: $expires
        Preferred-Languages: en
        Canonical: https://elephantchess.io/.well-known/security.txt
    """.trimIndent() + "\n"
}
