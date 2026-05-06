package io.elephantchess.utils

object ResourceUtils {

    fun resourceAsString(filePath: String): String? {
        return ResourceUtils::class.java.getResource(filePath)?.readText()
    }

    fun resourceAsLines(filePath: String): List<String> {
        return ResourceUtils::class.java.getResource(filePath)?.readText()?.lines() ?: emptyList()
    }

}
