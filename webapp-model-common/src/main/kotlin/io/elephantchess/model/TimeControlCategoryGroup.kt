package io.elephantchess.model

/**
 * Coarser grouping of [TimeControlCategory] used for reporting, where the fast real-time categories
 * (BULLET, BLITZ, RAPID) are collapsed together and the slow correspondence-like categories
 * (SEVERAL_DAYS, CORRESPONDENCE) are collapsed together, keeping CLASSICAL on its own.
 */
enum class TimeControlCategoryGroup {

    REAL_TIME,
    CLASSICAL,
    CORRESPONDENCE,
    ;

    companion object {

        fun of(category: TimeControlCategory): TimeControlCategoryGroup =
            when (category) {
                TimeControlCategory.BULLET,
                TimeControlCategory.BLITZ,
                TimeControlCategory.RAPID,
                -> REAL_TIME

                TimeControlCategory.CLASSICAL -> CLASSICAL

                TimeControlCategory.SEVERAL_DAYS,
                TimeControlCategory.CORRESPONDENCE,
                -> CORRESPONDENCE
            }
    }
}
