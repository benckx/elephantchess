package io.elephantchess.servicelayer.utils

import io.elephantchess.engines.protocol.commands.EngineProcessLocator

object DockerizedProcessLocator : EngineProcessLocator {

    override fun launchCommand(binFileName: String) =
        "/bin/bash -lc /app/engines/$binFileName"

}
