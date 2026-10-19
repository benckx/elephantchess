package io.elephantchess.utils

object StringUtils {

    fun String.capitalize(): String = lowercase().replaceFirstChar(Char::titlecase)
}
