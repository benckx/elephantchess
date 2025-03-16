package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator

/**
 * Identifies an engine process.
 * Typically, you will have one instance of Pikafish and one instance of Fairy Stockfish.
 * But you could also have multiple instances of Fairy Stockfish with different variants (one for xiangqi and one for janggi).
 */
abstract class EngineId {

    abstract val id: String
    abstract val displayName: String

    /**
     * For example Pikafish doesn't like non-standard FENs (e.g. more than 2 rooks) and will always time out on those
     */
    open val supportsNonStandardFens = false

    abstract fun pathOfExecutable(version: String?): String

    abstract fun makeProcess(config: EngineConfig, engineProcessLocator: EngineProcessLocator): EngineProcess

    override fun toString() = displayName

    override fun equals(other: Any?) = (other is EngineId) && (other.id == id)

    override fun hashCode() = id.hashCode()

}
