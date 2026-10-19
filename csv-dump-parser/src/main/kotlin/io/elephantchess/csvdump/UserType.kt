package io.elephantchess.csvdump

/**
 * Type of account behind a player, as recorded in the CSV dump.
 */
enum class UserType {
    AUTHENTICATED,
    GUEST;

    companion object {

        fun fromCsv(value: String): UserType =
            when (val trimmed = value.trim().uppercase()) {
                "AUTHENTICATED" -> AUTHENTICATED
                "GUEST" -> GUEST
                else -> throw IllegalArgumentException("unknown user type: '$trimmed'")
            }
    }
}
