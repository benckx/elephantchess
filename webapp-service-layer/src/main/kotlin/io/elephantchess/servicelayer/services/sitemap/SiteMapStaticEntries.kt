package io.elephantchess.servicelayer.services.sitemap

import io.elephantchess.servicelayer.services.sitemap.ChangeFrequency.DAILY
import io.elephantchess.servicelayer.services.sitemap.ChangeFrequency.MONTHLY
import io.elephantchess.servicelayer.services.sitemap.ChangeFrequency.WEEKLY
import java.time.LocalDate

internal fun siteMapStaticEntries(webHostTrimmed: String, deployDay: LocalDate): List<SitemapEntry> {
    val howToPlayXiangqiLastPageChange = LocalDate.of(2026, 5, 24)
    val howToPlayJanggiLastPageChange = LocalDate.of(2026, 11, 4)
    val xiangqiTestBoardLastChange = LocalDate.of(2026, 5, 23)
    val manchuTestBoardLastChange = LocalDate.of(2026, 11, 4)
    val janggiTestBoardLastChange = LocalDate.of(2026, 11, 4)
    val sevenKingdomLastChange = LocalDate.of(2025, 12, 10)

    return listOf(
        SitemapEntry(
            loc = "$webHostTrimmed/",
            lastmod = deployDay,
            changefreq = MONTHLY,
            priority = 1.0,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about",
            lastmod = LocalDate.of(2026, 6, 14),
            changefreq = MONTHLY,
            priority = 1.0,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about/changelog",
            lastmod = deployDay,
            changefreq = MONTHLY,
            priority = 0.9,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about/faq",
            lastmod = LocalDate.of(2026, 1, 18),
            changefreq = MONTHLY,
            priority = 0.9,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about/policy",
            lastmod = LocalDate.of(2026, 11, 14),
            changefreq = MONTHLY,
            priority = 0.9,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about/datasets",
            lastmod = LocalDate.of(2026, 6, 14),
            changefreq = MONTHLY,
            priority = 0.9,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/about/developers/board-gui-example",
            lastmod = LocalDate.of(2026, 4, 30),
            changefreq = MONTHLY,
            priority = 0.9,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/database",
            lastmod = LocalDate.of(2026, 1, 25),
            changefreq = MONTHLY,
            priority = 0.7,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/database/events",
            lastmod = LocalDate.of(2026, 1, 25),
            changefreq = WEEKLY,
            priority = 0.8,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/database/players",
            lastmod = LocalDate.of(2026, 1, 25),
            changefreq = MONTHLY,
            priority = 0.8,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/how-to-play-xiangqi",
            lastmod = howToPlayXiangqiLastPageChange,
            changefreq = MONTHLY,
            priority = 0.8,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/how-to-play-janggi",
            lastmod = howToPlayJanggiLastPageChange,
            changefreq = MONTHLY,
            priority = 0.8,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/xiangqi-test-board",
            lastmod = xiangqiTestBoardLastChange,
            changefreq = MONTHLY,
            priority = 0.7,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/manchu-test-board",
            lastmod = manchuTestBoardLastChange,
            changefreq = MONTHLY,
            priority = 0.7,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/janggi-test-board",
            lastmod = janggiTestBoardLastChange,
            changefreq = MONTHLY,
            priority = 0.7,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/global",
            lastmod = LocalDate.now(),
            changefreq = DAILY,
            priority = 0.7,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/analysis",
            lastmod = LocalDate.of(2025, 12, 10),
            changefreq = MONTHLY,
            priority = 0.6,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/7k/about",
            lastmod = sevenKingdomLastChange,
            changefreq = MONTHLY,
            priority = 0.7,
        ),
        SitemapEntry(
            loc = "$webHostTrimmed/7k/playground",
            lastmod = sevenKingdomLastChange,
            changefreq = MONTHLY,
            priority = 0.6,
        ),
    )
}
