package io.elephantchess.webapp

import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.elephantchess.config.AppConfig
import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.servicelayer.clients.DigitalOceanSpacesClient
import io.elephantchess.servicelayer.utils.ops.singleAuto
import io.elephantchess.webapp.rendering.*
import io.elephantchess.webapp.sitemap.SiteMapService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.util.jar.JarFile

private val webAppLogger = KotlinLogging.logger {}

fun webAppKoinModule(eagerAllowed: Boolean): Module = module {
    singleAuto<SiteMapService>(eager = eagerAllowed)
    includes(htmlRendering())
    includes(pageRendererModule(eagerAllowed))
    single(named("wsJsonMapper")) {
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .configure(INDENT_OUTPUT, false)
            .build()
    }
}

private fun htmlRendering() = module {
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
                webTemplateRenderer = webTemplateRenderer
            )
        }
    }
}

private fun pageRendererModule(eagerAllowed: Boolean) = module {
    singleAuto<SimplePageRenderer>(eager = eagerAllowed)
    singleAuto<UserProfilePageRenderer>(eager = eagerAllowed)
    singleAuto<DatabasePageRenderer>(eager = eagerAllowed)
    singleAuto<BoardGuiExampleRenderer>(eager = eagerAllowed)
    singleAuto<ModalRenderer>(eager = eagerAllowed)
    singleAuto<FaqPageRenderer>(eager = eagerAllowed)
    singleAuto<ChangelogPageRenderer>(eager = eagerAllowed)
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
            val jarFile = File(java.net.URI(jarPath))
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

private suspend fun mostRecentVersionFolder(
    client: DigitalOceanSpacesClient
): String? {
    return try {
        client.getMostRecentVersionFolder()
    } catch (e: Exception) {
        webAppLogger.error { "error getting most recent CDN version folder from Space: $e" }
        null
    }
}
