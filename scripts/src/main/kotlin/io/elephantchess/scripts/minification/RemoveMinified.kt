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

    // Optionally clean up the CSV file
    val csvFile = File("minified_files.csv")
    if (csvFile.exists()) {
        println("[deleting] ${csvFile.path}")
        csvFile.delete()
        println("deleted CSV tracking file")
    } else {
        println("CSV tracking file not found")
    }

    println()
    println("cleanup complete!")
}
