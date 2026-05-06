package io.elephantchess.scripts.utils

import io.elephantchess.utils.ResourceUtils.resourceAsString
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.StringReader
import java.util.*

/**
 * configurationLocation exists so I can use a config file from outside the repo when working with a prod backup
 */
data class ScriptConfig(
    val appProfile: String?,
    val configurationLocation: String?
) {

    companion object {

        private val logger = KotlinLogging.logger {}

        fun loadScriptConfig(): ScriptConfig {
            val propertiesStr = resourceAsString("/scripts.properties")
            if (propertiesStr != null) {
                val props = Properties()
                props.load(StringReader(propertiesStr))
                val appProfile = props.getProperty("app.profile")
                val configurationLocation = props.getProperty("configuration.location")
                logger.info { "scripts.properties -> app.profile=$appProfile" }
                logger.info { "scripts.properties -> configuration.location=$configurationLocation" }
                return ScriptConfig(
                    appProfile = appProfile,
                    configurationLocation = configurationLocation
                )
            } else {
                logger.warn { "scripts.properties not found" }
                return ScriptConfig(
                    appProfile = null,
                    configurationLocation = null
                )
            }
        }

    }

}
