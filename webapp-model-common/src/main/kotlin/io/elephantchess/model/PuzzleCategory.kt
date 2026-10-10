package io.elephantchess.model

enum class PuzzleCategory {

    // represents the number of half-moves the player has to do
    MATE_IN_1,
    MATE_IN_2,
    MATE_IN_3,
    MATE_IN_4,
    MATE_IN_5,
    MATE_IN_6,
    MATE_IN_7,
    MATE_IN_8,
    FORK,
    PIN,
    DISCOVERED_ATTACK;

    fun isMateIn(): Int? {
        return when (this) {
            MATE_IN_1 -> 1
            MATE_IN_2 -> 2
            MATE_IN_3 -> 3
            MATE_IN_4 -> 4
            MATE_IN_5 -> 5
            MATE_IN_6 -> 6
            MATE_IN_7 -> 7
            MATE_IN_8 -> 8
            else -> null
        }
    }

    companion object {

        fun findMateInN(n: Int): PuzzleCategory {
            return when (n) {
                1 -> MATE_IN_1
                2 -> MATE_IN_2
                3 -> MATE_IN_3
                4 -> MATE_IN_4
                5 -> MATE_IN_5
                6 -> MATE_IN_6
                7 -> MATE_IN_7
                8 -> MATE_IN_8
                else -> throw IllegalArgumentException()
            }
        }

        fun parseList(list: List<String>): List<PuzzleCategory> {
            return list.map { valueOf(it) }
        }

    }

}
