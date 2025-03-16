package io.elephantchess.engines.protocol.commands

object LocalProcessLocator : EngineProcessLocator {

    override fun launchCommand(binFileName: String) = "./engines/$binFileName"

}
