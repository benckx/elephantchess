package io.elephantchess.xiangqi.testutils

object ResourcesUtils {

    fun getResourceAsText(path: String): String =
        object {}.javaClass.getResource(path)?.readText()!!

    fun loadDigitsStringGame(fileName: String): String {
        return getResourceAsText("/$fileName")
            .filter { char -> char.isDigit() }
    }

}
