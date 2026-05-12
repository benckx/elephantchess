package io.elephantchess.config

data class AppConfig(
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
    val properties: PropertiesFile,
) {

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
        return lines
    }

    fun loadString(key: String): String {
        return properties.loadString(key)
    }

    fun loadBoolean(key: String, default: Boolean): Boolean {
        return properties.loadBoolean(key, default)
    }

}
