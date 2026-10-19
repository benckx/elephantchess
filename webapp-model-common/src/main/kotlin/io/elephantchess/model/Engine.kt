package io.elephantchess.model

enum class Engine {

    PIKAFISH,
    FAIRYSTOCKFISH,
    ;

    override fun toString(): String =
        when (this) {
            PIKAFISH -> "Pikafish"
            FAIRYSTOCKFISH -> "Fairy"
        }
}
