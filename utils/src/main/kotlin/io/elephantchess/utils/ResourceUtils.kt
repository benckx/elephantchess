package io.elephantchess.utils

object ResourceUtils {

    fun resourceAsString(filePath: String): String? =
        ResourceUtils::class.java.getResource(filePath)?.readText()

    fun resourceAsLines(filePath: String): List<String> =
        ResourceUtils::class.java.getResource(filePath)?.readText()?.lines() ?: emptyList()
}
