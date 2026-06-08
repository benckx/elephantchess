package io.elephantchess.scripts.minification

import java.io.File

fun main() {
    val prefix = "webapp/src/main/resources/public/dist/0.0.2/"
    val files =
        listOf(
            "board.css",
            "xiangqi.js",
            "board-gui.js"
        )
            .map { prefix + it }
            .map { File(it) }

    minifyAll(files)
}
