package io.elephantchess.sevenkingdoms

/**
 * Abstract in this context meaning "independently of color"
 */
enum class AbstractPieceType(
    val chineseName: String,
    val uci: String
) {

    GENERAL("將", "Q"),
    CHANCELLOR("偏", "R"),
    DIPLOMAT("裨", "B"),
    CANNON("砲", "C"),
    GO_BETWEEN("行人", "G"),
    ARCHER("弓", "H"),
    CROSSBOWMAN("弩", "W"),
    DAGGER_SOLDIER("刀", "A"),
    SWORDSMAN("劍", "S"),
    KNIGHT("騎", "N");

    override fun toString(): String {
        return name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
            .replace("_", " ")
    }

    companion object {

        fun getPieceTypeByUci(uci: Char): AbstractPieceType {
            return entries.find { it.uci == uci.toString().uppercase() }
                ?: throw IllegalArgumentException("Not found piece type for UCI $uci")
        }

    }

}
