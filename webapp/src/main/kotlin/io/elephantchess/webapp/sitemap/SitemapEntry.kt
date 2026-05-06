package io.elephantchess.webapp.sitemap

import java.time.LocalDate

/**
 * Represents a single URL entry in the sitemap.xml file.
 *
 * @property loc The URL of the page. Must be absolute URL starting with http(s)://
 * @property lastmod The date of last modification in YYYY-MM-DD format
 * @property changefreq How frequently the page is likely to change
 * @property priority The priority of this URL relative to other URLs on the site (0.0 to 1.0)
 */
data class SitemapEntry(
    val loc: String,
    val lastmod: LocalDate,
    val changefreq: ChangeFrequency,
    val priority: Double
) {
    init {
        require(priority in 0.0..1.0) { "Priority must be between 0.0 and 1.0" }
        require(loc.startsWith("http://") || loc.startsWith("https://")) {
            "Location must be an absolute URL starting with http:// or https://"
        }
    }
}

enum class ChangeFrequency(val value: String) {
    ALWAYS("always"),
    HOURLY("hourly"),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly"),
    NEVER("never")
}
