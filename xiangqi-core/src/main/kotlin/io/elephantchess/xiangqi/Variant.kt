package io.elephantchess.xiangqi

/**
 * The chess variant being played.
 */
enum class Variant {

    XIANGQI,

    /**
     * Manchu chess (also known as Yitong). Similar to xiangqi but Red's pieces are
     * reduced to: general, 2 advisors, 2 elephants, 5 soldiers, and a super-chariot (Banner)
     * that combines the powers of the chariot, horse, and cannon.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Manchu_chess">Wikipedia: Manchu chess</a>
     */
    MANCHU;

    /**
     * The Chinese character commonly used to represent this variant.
     */
    fun chineseChar(): String = when (this) {
        XIANGQI -> "象"
        MANCHU -> "统"
    }

    /**
     * The Fairy Stockfish variant name for this variant (used with `setoption name UCI_Variant value <name>`).
     */
    fun fairyStockfishVariantName(): String = when (this) {
        XIANGQI -> "xiangqi"
        MANCHU -> "manchu"
    }

}
