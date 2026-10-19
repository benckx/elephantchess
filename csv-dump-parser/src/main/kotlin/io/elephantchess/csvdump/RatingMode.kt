package io.elephantchess.csvdump

/**
 * Whether a game affected the players' rating.
 */
enum class RatingMode {
    RATED,
    CASUAL;

    companion object {

        fun fromCsv(value: String): RatingMode =
            when (value.trim().lowercase()) {
                "rated" -> RATED
                "casual" -> CASUAL
                else -> throw IllegalArgumentException("unknown rating mode: '$value'")
            }
    }
}
