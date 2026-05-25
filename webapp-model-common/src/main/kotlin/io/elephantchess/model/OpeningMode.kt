package io.elephantchess.model

enum class OpeningMode {

    /**
     * Pick the next move weighted by occurrence frequency in the opening repertoire.
     */
    BY_FREQUENCY,

    /**
     * Pick a random move from the opening repertoire.
     */
    RANDOM,

    /**
     * Ignore the opening repertoire and let the engine play from the start.
     */
    ENGINE_ONLY

}
