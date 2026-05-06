package io.elephantchess.sevenkingdoms

enum class Color(
    val fenColorChar: Char,
    val armyName: String,
    val armyChineseName: String,
    val colorCode: String,
) {
    // counter-clock wise order (order in which we play)
    WHITE('w', "Qin", "秦", "#FFFFFF"),
    RED('r', "Chu", "楚", "#ca0808"),
    ORANGE('o', "Han", "韓", "rgb(253, 189, 71)"),
    BLUE('b', "Qi", "齊", "rgb(6, 6, 211)"),
    GREEN('g', "Wei", "魏", "rgb(1, 83, 1)"),
    PURPLE('p', "Zhao", "趙", "rgb(98, 1, 98)"),
    BLACK('d', "Yan", "燕", "#000000");

    fun next(): Color =
        entries[(ordinal + 1) % entries.size]

    fun capitalizedName(): String =
        name.lowercase().replaceFirstChar { it.uppercase() }

    override fun toString() = capitalizedName()

    companion object {

        fun getColorFromFenColorChar(char: Char): Color =
            entries.find { it.fenColorChar == char.lowercaseChar() }
                ?: throw IllegalArgumentException("Not found color $char")

        fun areContiguous(colors: List<Color>): Boolean =
            when (colors.size) {
                0 -> false
                1 -> false
                else -> colors.dropLast(1).all { it.next() == colors[colors.indexOf(it) + 1] }
            }

    }

}
