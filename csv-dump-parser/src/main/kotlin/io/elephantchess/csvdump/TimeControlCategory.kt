package io.elephantchess.csvdump

/**
 * Speed category of the game's time control.
 */
enum class TimeControlCategory {
    BULLET,
    BLITZ,
    RAPID,
    CLASSICAL,
    SEVERAL_DAYS,
    CORRESPONDENCE;

    companion object {

        fun fromCsv(value: String): TimeControlCategory {
            val trimmed = value.trim().uppercase()
            return entries.firstOrNull { it.name == trimmed }
                ?: throw IllegalArgumentException("unknown time control category: '$trimmed'")
        }
    }
}
