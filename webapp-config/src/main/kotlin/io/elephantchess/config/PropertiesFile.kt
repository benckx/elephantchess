package io.elephantchess.config

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.StringReader
import java.net.URL
import java.nio.file.Files
import java.util.*
import kotlin.io.path.Path

class PropertiesFile(
    private val profileName: String,
    private val configurationLocation: String?
) {

    private val logger = KotlinLogging.logger {}

    private val properties = loadProperties()

    fun loadString(key: String) = getProperty(key)

    fun loadBoolean(key: String): Boolean {
        return try {
            getProperty(key).toBoolean()
        } catch (e: Exception) {
            logger.error { "error loading boolean property $key: $e" }
            false
        }
    }

    fun loadBoolean(key: String, default: Boolean) = getPropertyOrNull(key)?.toBoolean() ?: default

    fun loadInt(key: String) = getProperty(key).toInt()

    fun loadList(key: String): List<String> {
        return if (!properties.containsKey(key)) {
            logger.warn { "list property not found $key" }
            listOf()
        } else {
            getProperty(key).split(",")
        }
    }

    private fun getProperty(key: String): String {
        val value = properties.getProperty(key)
        if (value == null) {
            logger.error { "property not found: $key" }
            throw RuntimeException("property not found: $key")
        } else {
            return value
        }
    }

    private fun getPropertyOrNull(key: String): String? {
        return properties.getProperty(key)
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        val baseContent = if (configurationLocation == null) {
            val configFileName = "/app_${profileName}.properties"
            logger.info { "loading properties from resource $configFileName" }
            resourceAsText(configFileName)
        } else {
            val filePath = if (configurationLocation.endsWith("/")) {
                "${configurationLocation}app_${profileName}.properties"
            } else {
                "$configurationLocation/app_${profileName}.properties"
            }

            logger.info { "loading properties from file $filePath" }
            Files.readString(Path(filePath))
        }
        properties.load(StringReader(baseContent))
        return properties
    }

    private fun resourceAsText(path: String): String {
        return resourceURL(path)!!.readText()
    }

    private fun resourceURL(path: String): URL? {
        return object {}.javaClass.getResource(path)
    }

}
