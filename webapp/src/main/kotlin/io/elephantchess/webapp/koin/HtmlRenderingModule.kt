package io.elephantchess.webapp.koin

import io.elephantchess.config.AppConfig
import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.servicelayer.clients.DigitalOceanSpacesClient
import io.elephantchess.webapp.rendering.WebFragmentResolver
import io.elephantchess.webapp.rendering.WebTemplateRenderer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import java.io.File
import java.net.URI
import java.util.jar.JarFile

private val webAppLogger = KotlinLogging.logger {}

internal fun htmlRenderingModule() =
    module {
        single {
            val fragmentTags = listFragmentTags()
            fragmentTags.forEach { webAppLogger.info { "discovered web fragment: $it" } }

            val appConfig = get<AppConfig>()
            val webFragmentsTagResolvers = fragmentTags.map { WebFragmentResolver(it) }

            val disabledTemplates = mutableListOf<String>()
            if (!appConfig.isGoogleAnalyticsEnabled) {
                disabledTemplates += "google_analytics"
            }
            if (!appConfig.isCookieConsentBannerEnabled) {
                disabledTemplates += "google_tag_manager_head"
                disabledTemplates += "google_tag_manager_body"
            }

            WebTemplateRenderer(webFragmentsTagResolvers, disabledTemplates)
        }
        single {
            runBlocking {
                val appConfig = get<AppConfig>()
                val webTemplateRenderer = get<WebTemplateRenderer>()
                val cdnFolder =
                    if (appConfig.cdnEnabled) {
                        mostRecentVersionFolder(get<DigitalOceanSpacesClient>())
                    } else {
                        null
                    }

                HtmlRenderer(
                    isMinificationEnabled = appConfig.isMinificationEnabled,
                    cdnFolder = cdnFolder,
                    webTemplateRenderer = webTemplateRenderer,
                )
            }
        }
    }

private fun listFragmentTags(): List<String> {
    val resourcePath = "/web_fragments"
    val resources = object {}.javaClass.getResource(resourcePath)
        ?: throw IllegalStateException("Resource directory not found: $resourcePath")

    return when (resources.protocol) {
        "file" -> {
            // Local/IDE development
            File(resources.toURI())
                .listFiles()
                ?.filter { it.extension == "html" }
                ?.map { it.nameWithoutExtension }
                ?.sorted()
                ?: emptyList()
        }

        "jar" -> {
            // JAR or containerized
            val jarPath = resources.path.substringBefore("!")
            val jarFile = File(URI(jarPath))
            JarFile(jarFile).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.startsWith("web_fragments/") && it.name.endsWith(".html") }
                    .map { it.name.substringAfterLast("/").removeSuffix(".html") }
                    .sorted()
                    .toList()
            }
        }

        else -> throw IllegalStateException("Unsupported protocol: ${resources.protocol}")
    }
}

private suspend fun mostRecentVersionFolder(client: DigitalOceanSpacesClient): String? =
    try {
        client.getMostRecentVersionFolder()
    } catch (e: Exception) {
        webAppLogger.error { "error getting most recent CDN version folder from Space: $e" }
        null
    }
