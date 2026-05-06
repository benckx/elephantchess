package io.elephantchess.config

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options

data class ArgConfig(
    val profile: String,
    val configLocation: String?
) {

    companion object {

        fun parseArgs(args: Array<String>): ArgConfig {
            val options = Options()
                .addOption(
                    Option.builder("p")
                        .longOpt("profile")
                        .hasArg()
                        .desc("Application profile (e.g., local, prod)")
                        .get()
                )
                .addOption(
                    Option.builder("cl")
                        .longOpt("configLocation")
                        .hasArg()
                        .desc("Configuration files location")
                        .get()
                )

            val cmd = DefaultParser().parse(options, args)

            val profile = cmd.getOptionValue("p", "local")
            val configLocation = cmd.getOptionValue("cl", "")

            return ArgConfig(
                profile = profile,
                configLocation = if (configLocation.isNullOrBlank()) null else configLocation
            )
        }

    }

}
