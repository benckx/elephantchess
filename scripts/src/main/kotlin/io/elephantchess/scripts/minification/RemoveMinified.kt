package io.elephantchess.scripts.minification

import io.elephantchess.scripts.listAllCssFiles
import io.elephantchess.scripts.listAllJsFiles
import java.io.File

private fun minifiedFile(file: File): File {
    val extension = file.extension
    val outputName = "${file.nameWithoutExtension}.min.$extension"
    return File(file.parentFile, outputName)
}

fun main() {
    val allFiles = listAllJsFiles() + listAllCssFiles()
    println("found ${allFiles.size} source files (JS and CSS)")

    val minifiedFiles = allFiles.map { file -> minifiedFile(file) }.filter { it.exists() }

    println("found ${minifiedFiles.size} minified files to delete")
    println()

    minifiedFiles.forEach { file ->
        println("[deleting] ${file.path}")
        file.delete()
    }

    println()
    println("deleted ${minifiedFiles.size} minified files")

    println()
    println("cleanup complete!")
}
