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
 *
 * - `/.well-known/assetlinks.json`: Digital Asset Links statement used by the Android Trusted Web
 *   Activity (TWA) wrapper to prove this site owns the app, which removes the browser URL bar.
 *   The package name and the signing-certificate SHA-256 fingerprints are provided via the
 *   `ANDROID_TWA_PACKAGE_NAME` and `ANDROID_TWA_SHA256_FINGERPRINTS` environment variables
 *   (the latter comma-separated). Both the upload key and the Play App Signing key fingerprint
 *   should be listed. When unset, an empty statement is served so the endpoint still exists.
 *   See https://developers.google.com/digital-asset-links/v1/getting-started
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

    val assetLinks = buildAssetLinks()

    routing {
        route("/.well-known/assetlinks.json") {
            get {
                call.respondText(
                    text = assetLinks,
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                )
            }
            head {
                call.response.status(HttpStatusCode.OK)
                call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                call.response.header(HttpHeaders.ContentLength, assetLinks.length.toString())
            }
        }

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

/**
 * Builds the Digital Asset Links JSON linking this website to the Android TWA app. The package name
 * and SHA-256 signing fingerprints are read from environment variables so the signing certificate
 * never has to be committed. When nothing is configured, an empty JSON array is served, which keeps
 * the endpoint present (no 404s) while simply not asserting any app relationship yet.
 */
private fun buildAssetLinks(): String {
    val packageName = System.getenv("ANDROID_TWA_PACKAGE_NAME")?.trim().orEmpty()
    val fingerprints = System.getenv("ANDROID_TWA_SHA256_FINGERPRINTS")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    if (packageName.isEmpty() || fingerprints.isEmpty()) {
        return "[]"
    }

    val fingerprintsJson = fingerprints.joinToString(separator = ",\n            ") { "\"$it\"" }

    return (
        """
        [
          {
            "relation": ["delegate_permission/common.handle_all_urls"],
            "target": {
              "namespace": "android_app",
              "package_name": "$packageName",
              "sha256_cert_fingerprints": [
        """.trimIndent() +
            "\n            " + fingerprintsJson + "\n" +
            """
              ]
            }
          }
        ]
            """.trimIndent() + "\n"
        )
}
