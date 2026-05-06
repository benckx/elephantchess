package io.elephantchess.model

enum class Engine {

    PIKAFISH,
    FAIRYSTOCKFISH;

    override fun toString(): String {
        return when (this) {
            PIKAFISH -> "Pikafish"
            FAIRYSTOCKFISH -> "Fairy"
        }
    }

}
