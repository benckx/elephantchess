package io.elephantchess.engines.protocol.commands

/**
 * Locate the process binary on the system
 */
interface EngineProcessLocator {

    fun launchCommand(binFileName: String): String

    fun launchArguments(binFileName: String): List<String> =
        launchCommand(binFileName).split(" ")
}
