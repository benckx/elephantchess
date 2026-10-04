package io.elephantchess.config

data class MailConfig(
    val smtpHost: String,
    val smtpPort: String,
    val smtpSslEnable: String,
    val smtpAuth: String,
    val username: String,
    val password: String,
)

data class AppConfig(
    // Required fields for base app functionality
    val profile: String,
    val webHost: String,
    val isMinificationEnabled: Boolean,
    val isGoogleAnalyticsEnabled: Boolean,
    val isCookieConsentBannerEnabled: Boolean,
    val isDockerized: Boolean,
    val sendMailNotifications: Boolean,
    val isCachingEnabled: Boolean,
    val isEnginePoolEnabled: Boolean,
    val enginesThreads: Int,
    val pikafishVersion: String,
    val fairyStockfishVersion: String,
    val dbConfig: DbConfig,
    val parseUserAgent: Boolean,
    val disabledBatches: List<String>,
    val cdnEnabled: Boolean,
    val symmetricKey: String,
    val salt: String,
    val properties: PropertiesFile,
) {

    // Lazy loaders for production-only features (email notifications, CDN config, etc.)
    val apiLayerApiKey: String
        get() = loadString("apilayer.apikey")

    val doSpacesKeyId: String
        get() = loadString("digitalocean.spaces.key.id")

    val doSpacesKeySecret: String
        get() = loadString("digitalocean.spaces.key.secret")

    val doSpacesBucket: String
        get() = loadString("digitalocean.spaces.bucket")

    val discordToken: String
        get() = loadString("discord.token")

    val emailListVerifyApiKey: String
        get() = loadString("emaillistverify.apikey")

    val discordSuffix: String
        get() = loadString("discord.suffix")

    val discordNotificationEnabled: Boolean
        get() = loadBoolean("discord.notification.enabled", false)

    val kofiVerificationToken: String
        get() = loadString("kofi.verification.token")

    val excludedFromAnalytics: List<String>
        get() = loadListOfStrings("excluded.from.analytics")

    val adminEmail: String
        get() = loadString("admin.email")

    /**
     * Per-IP rate limiting for the public reference-database HTML pages (`/database/...`, `/browse/event`).
     * These are the pages targeted by scrapers (player and game enumeration). Well-known search/AI crawler
     * user-agents are always exempt (see DatabaseRateLimiter), so this does not affect SEO.
     */
    val isDatabaseRateLimitEnabled: Boolean
        get() = loadBoolean("database.rate.limit.enabled", true)

    /** Maximum number of monitored database requests allowed per IP within [databaseRateLimitWindowSeconds]. */
    val databaseRateLimitMaxRequests: Int
        get() = loadIntOrDefault("database.rate.limit.max.requests", 40)

    /** Length of the rate-limit window, in seconds. */
    val databaseRateLimitWindowSeconds: Int
        get() = loadIntOrDefault("database.rate.limit.window.seconds", 60)

    val mailConfig: MailConfig
        get() = MailConfig(
            smtpHost = loadString("mail.smtp.host"),
            smtpPort = loadString("mail.smtp.port"),
            smtpSslEnable = loadString("mail.smtp.ssl.enable"),
            smtpAuth = loadString("mail.smtp.auth"),
            username = loadString("mail.username"),
            password = loadString("mail.password"),
        )

    fun toStringMultilines(): List<String> {
        val lines = mutableListOf<String>()
        lines += "webHost -> $webHost"
        lines += "minified JS -> $isMinificationEnabled"
        lines += "Google Analytics -> $isGoogleAnalyticsEnabled"
        lines += "cookie consent banner -> $isCookieConsentBannerEnabled"
        lines += "dockerized -> $isDockerized"
        lines += "send mail notifications -> $sendMailNotifications"
        lines += "caching enabled -> $isCachingEnabled"
        lines += "engines -> $isEnginePoolEnabled"
        lines += "enginesThreads -> $enginesThreads"
        lines += "pikafishVersion -> $pikafishVersion"
        lines += "fairyStockfishVersion -> $fairyStockfishVersion"
        lines += "db name -> ${dbConfig.dbName}"
        lines += "db url -> ${dbConfig.url}"
        lines += "parseUserAgent -> $parseUserAgent"
        lines += "disabled batches -> ${disabledBatches.joinToString(", ")}"
        lines += "CDN enabled -> $cdnEnabled"
        lines += "database rate limit -> $isDatabaseRateLimitEnabled ($databaseRateLimitMaxRequests req / ${databaseRateLimitWindowSeconds}s per IP)"
        return lines
    }

    private fun loadString(key: String): String {
        return properties.loadString(key)
    }

    private fun loadListOfStrings(key: String): List<String> {
        return properties.loadString(key).split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun loadBoolean(key: String, default: Boolean): Boolean {
        return properties.loadBoolean(key, default)
    }

    private fun loadIntOrDefault(key: String, default: Int): Int {
        return properties.loadIntOrDefault(key, default)
    }

}
