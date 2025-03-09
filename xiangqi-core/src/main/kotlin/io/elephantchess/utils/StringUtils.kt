package io.elephantchess.utils

object StringUtils {

    fun String.capitalize(): String {
        return lowercase().replaceFirstChar(Char::titlecase)
    }

}
