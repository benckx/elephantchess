package io.elephantchess.model

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

enum class TimeControlCategory {

    BULLET,
    BLITZ,
    RAPID,
    CLASSICAL,
    SEVERAL_DAYS, // TODO: actually deprecate this category
    CORRESPONDENCE;

    companion object {

        fun fromSeconds(seconds: Int): TimeControlCategory {
            return when {
                seconds < 3.minutes.inWholeSeconds -> BULLET
                seconds <= 5.minutes.inWholeSeconds -> BLITZ
                seconds < 1.hours.inWholeSeconds -> RAPID
                seconds < 1.days.inWholeSeconds -> CLASSICAL
//                seconds <= 7.days.inWholeSeconds -> SEVERAL_DAYS
                else -> CORRESPONDENCE
            }
        }

    }

}
