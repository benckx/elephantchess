package io.elephantchess.servicelayer.services.sitemap

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.services.DatabaseService
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.elephantchess.servicelayer.services.sitemap.ChangeFrequency.MONTHLY
import io.elephantchess.servicelayer.services.sitemap.ChangeFrequency.WEEKLY
import io.github.oshai.kotlinlogging.KLogger
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SiteMapService(
    private val databaseService: DatabaseService,
    private val userService: UserService,
    refresherScope: CoroutineScope,
    appConfig: AppConfig,
    private val logger: KLogger
) {

    private val deployDay = LocalDate.now()
    private val howToPlayLastPageChange = LocalDate.of(2026, 5, 24)
    private val sevenKingdomLastChange = LocalDate.of(2025, 12, 10)

    private val webHostTrimmed = appConfig.webHost.trimEnd('/')

    private val siteMapCache =
        Cache
            .Builder<String, String>()
            .expireAfterWrite(12.hours)
            .build()

    private val refreshJob = launchAtFixedRate(
        scope = refresherScope,
        initialDelay = 1.minutes,
        period = 6.hours
    ) {
        val xml = getSiteMapXml()
        logger.info { "refreshed sitemap of ${xml.length} chars. in memory" }
    }

    fun cancel() {
        refreshJob.cancel()
    }

    private val staticEntries = listOf(
        SitemapEntry(
            loc = "$webHostTrimmed/",
            lastmod = deployDay,
            changefreq = MONTHLY,
            priority = 1.0
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about",
            lastmod = LocalDate.of(2026, 1, 5),
            changefreq = MONTHLY,
            priority = 1.0
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about/changelog",
            lastmod = deployDay,
            changefreq = MONTHLY,
            priority = 0.9
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about/faq",
            lastmod = LocalDate.of(2026, 1, 18),
            changefreq = MONTHLY,
            priority = 0.8
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about/developers/board-gui-example",
            lastmod = LocalDate.of(2026, 4, 30),
            changefreq = MONTHLY,
            priority = 0.7
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/database",
            lastmod = LocalDate.of(2026, 1, 25),
            changefreq = MONTHLY,
            priority = 0.7
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/database/events",
            lastmod = LocalDate.of(2026, 1, 25),
            changefreq = WEEKLY,
            priority = 0.8
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/database/players",
            lastmod = LocalDate.of(2026, 1, 25),
            changefreq = MONTHLY,
            priority = 0.8
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/how-to-play-xiangqi",
            lastmod = howToPlayLastPageChange,
            changefreq = MONTHLY,
            priority = 0.8
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/7k/about",
            lastmod = sevenKingdomLastChange,
            changefreq = MONTHLY,
            priority = 0.8
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/global",
            lastmod = LocalDate.now(),
            changefreq = WEEKLY,
            priority = 0.7
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/analysis",
            lastmod = LocalDate.of(2025, 12, 10),
            changefreq = MONTHLY,
            priority = 0.6
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/7k/playground",
            lastmod = sevenKingdomLastChange,
            changefreq = MONTHLY,
            priority = 0.6
        )
    )

    suspend fun getSiteMapXml(): String {
        return siteMapCache.get("sitemap") {
            val entries = mutableListOf<SitemapEntry>()
            val millis = measureTimeMillis {
                val playerEntries =
                    databaseService
                        .listPlayersForSiteMap(100)
                        .map { (canonicalName, lastModDate) ->
                            SitemapEntry(
                                loc = "$webHostTrimmed/database/player/${canonicalName.replace(" ", "_")}",
                                lastmod = lastModDate,
                                changefreq = MONTHLY,
                                priority = 0.7
                            )
                        }

                val userEntries =
                    userService
                        .listUsersForSiteMap()
                        .map { (username, lastProfileUpdate) ->
                            SitemapEntry(
                                loc = "$webHostTrimmed/@/$username",
                                lastmod = lastProfileUpdate,
                                changefreq = MONTHLY,
                                priority = 0.6
                            )
                        }

                entries += staticEntries + playerEntries + userEntries
                logger.info { "static entries: ${staticEntries.size}, db players entries: ${playerEntries.size}, user entries: ${userEntries.size}" }
            }
            logger.info { "generated sitemap with ${entries.size} entries in $millis ms." }
            generateXml(entries)
        }
    }

    /**
     * Generates the complete sitemap XML as a string.
     */
    private fun generateXml(entries: List<SitemapEntry>): String {
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")

            entries.forEach { entry ->
                appendLine("""    <url>""")
                appendLine("""        <loc>${entry.loc}</loc>""")
                appendLine("""        <lastmod>${entry.lastmod}</lastmod>""")
                appendLine("""        <changefreq>${entry.changefreq.value}</changefreq>""")
                appendLine("""        <priority>${entry.priority}</priority>""")
                appendLine("""    </url>""")
            }

            appendLine("""</urlset>""")
        }
    }

}
