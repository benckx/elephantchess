package io.elephantchess.config

import io.github.oshai.kotlinlogging.KotlinLogging.logger

private val logger = logger {}

fun loadAppConfig(argConfig: ArgConfig): AppConfig {
    logger.warn { "initializing AppConfig for profile ${argConfig.profile}" }
    val properties = PropertiesFile(argConfig.profile, argConfig.configLocation)
    val dbName = properties.loadString("db")
    val dbConfig = DbConfig(
        dbName = dbName,
        url = properties.loadString("$dbName.db.url"),
        user = properties.loadString("$dbName.db.user"),
        password = properties.loadString("$dbName.db.password"),
    )
    val appConfig = AppConfig(
        profile = argConfig.profile,
        webHost = properties.loadString("host"),
        isMinificationEnabled = properties.loadBoolean("minify"),
        isDockerized = properties.loadBoolean("dockerized"),
        isGoogleAnalyticsEnabled = properties.loadBoolean("ga"),
        isCookieConsentBannerEnabled = properties.loadBoolean("cookieConsentBanner"),
        sendMailNotifications = properties.loadBoolean("sendmails"),
        isCachingEnabled = properties.loadBoolean("caching"),
        isEnginePoolEnabled = properties.loadBoolean("engines"),
        enginesThreads = properties.loadInt("enginesThreads"),
        pikafishVersion = properties.loadString("pikafishVersion"),
        fairyStockfishVersion = properties.loadString("fairyStockfishVersion"),
        dbConfig = dbConfig,
        parseUserAgent = properties.loadBoolean("parseUserAgent"),
        disabledBatches = properties.loadList("disabled.batches"),
        cdnEnabled = properties.loadBoolean("cdn.enabled"),
        symmetricKey = properties.loadString("key"),
        salt = properties.loadString("salt"),
        properties = properties,
    )
    appConfig.toStringMultilines().forEach { line -> logger.info { line } }
    return appConfig
}
