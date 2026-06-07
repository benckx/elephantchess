package io.elephantchess.scripts.minification

import io.elephantchess.scripts.listAllCssFiles
import io.elephantchess.scripts.listAllJsFiles
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.Instant

const val CSV_FILE = "minified_files.csv"

/**
 * Local Node project that minifies assets without any network round-trip:
 * JavaScript with SWC (https://swc.rs, the same engine the previously used Toptal
 * endpoint is based on) and CSS with Lightning CSS (https://lightningcss.dev).
 */
private val MINIFIER_DIR = File("scripts/minifier")
private val MINIFIER_SCRIPT = File(MINIFIER_DIR, "minify.mjs")

private data class MinifyResult(
    val responseCode: Int,
)

private data class MinificationCsvEntry(
    val filePath: String,
    val checksum: String,
    val dateTime: Instant,
) {

    override fun hashCode(): Int {
        return filePath.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is MinificationCsvEntry && filePath == other.filePath
    }

    fun toCsv(): String {
        return "$filePath,$checksum,$dateTime"
    }
}

private fun minifiedFile(file: File): File {
    val extension = file.extension
    val outputName = "${file.nameWithoutExtension}.min.$extension"
    return File(file.parentFile, outputName)
}

private fun minifyFile(file: File): MinifyResult {
    val outputFile = minifiedFile(file)
    outputFile.delete()

    return when (file.extension) {
        "js" -> minifyWithNode(file, outputFile, "js")
        "css" -> minifyWithNode(file, outputFile, "css")
        else -> throw IllegalArgumentException("unsupported extension ${file.extension}")
    }
}

/**
 * Ensures the local minifier dependencies are installed (runs `npm install`
 * the first time, when `node_modules` is missing).
 */
private fun ensureMinifierInstalled() {
    if (File(MINIFIER_DIR, "node_modules/@swc/core").exists() &&
        File(MINIFIER_DIR, "node_modules/lightningcss").exists()
    ) {
        return
    }

    println("[minifier] installing dependencies in ${MINIFIER_DIR.path} ...")
    val exitCode = try {
        ProcessBuilder("npm", "install")
            .directory(MINIFIER_DIR)
            .inheritIO()
            .start()
            .waitFor()
    } catch (e: java.io.IOException) {
        println("ERROR: failed to start 'npm' (is Node.js/npm installed and on the PATH?): ${e.message}")
        exitProcess(1)
    }

    if (exitCode != 0) {
        println("ERROR: 'npm install' failed with code $exitCode")
        exitProcess(1)
    }
}

/**
 * Minifies a file locally by piping its content through the Node [MINIFIER_SCRIPT],
 * which uses SWC for `js` and Lightning CSS for `css`. Returns code 200 on success.
 */
private fun minifyWithNode(file: File, outputFile: File, type: String): MinifyResult {
    val process = try {
        ProcessBuilder("node", MINIFIER_SCRIPT.path, type).start()
    } catch (e: java.io.IOException) {
        println("ERROR: failed to start 'node' (is Node.js installed and on the PATH?): ${e.message}")
        exitProcess(1)
    }

    process.outputStream.use { stdin -> stdin.write(file.readBytes()) }

    val output = process.inputStream.readBytes().decodeToString()
    val errors = process.errorStream.readBytes().decodeToString()
    val exitCode = process.waitFor()

    if (exitCode == 0) {
        outputFile.writeText(output)
        return MinifyResult(200)
    }

    println("[minifier] failed to minify ${file.path}: $errors")
    return MinifyResult(500)
}

private fun checksum(file: File): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(file.readBytes())).toString(16).padStart(32, '0')
}

/**
 * To ensure we don't minify multiple times the same file if it didn't change,
 * therefore speeding up the minification script.
 */
private fun loadCsv(): List<MinificationCsvEntry> {
    val csvFile = File(CSV_FILE)
    val entries = mutableListOf<MinificationCsvEntry>()
    if (csvFile.exists()) {
        csvFile.readLines().forEach { line ->
            val (filePath, checksum, dateTime) = line.split(",")
            entries.add(MinificationCsvEntry(filePath, checksum, Instant.parse(dateTime)))
        }
    }
    return entries.toList()
}

private fun writeCsv(entries: List<MinificationCsvEntry>) {
    val csvFile = File(CSV_FILE)
    csvFile.delete()
    csvFile.createNewFile()
    csvFile.writeText(entries.sortedBy { it.filePath }.joinToString("\n") { entry -> entry.toCsv() })
    println("wrote ${entries.size} entries to $csvFile")
}

private fun sizeUnminified(files: List<File>): Long {
    return files.sumOf { file -> file.length() }
}

private fun sizeMinified(files: List<File>): Long {
    return files.sumOf { file -> minifiedFile(file).length() }
}

private fun formatBytes(bytes: Long): String {
    return "${bytes / 1024} kB"
}

/**
 * Minifies the given files if they have no up-to-date minified counterpart.
 * A file is considered up-to-date when an entry exists in [CSV_FILE], the
 * minified file exists on disk, and the source checksum still matches.
 */
fun minifyIfNeeded(files: List<File>) {
    files.forEach { file -> println("[found] ${file.path}") }

    val csvEntries = loadCsv().toMutableList()

    csvEntries
        .filterNot { entry -> File(entry.filePath).exists() }
        .toList()
        .forEach { entry ->
            println("[file not found] ${entry.filePath}")
            csvEntries.remove(entry)
        }

    val filesToMinify = files.filter { file ->
        val entry = csvEntries.find { entry -> entry.filePath == file.path }
        if (entry == null) {
            println("[entry not found] ${file.path}")
            true
        } else if (!minifiedFile(file).exists()) {
            println("[not minified] ${file.path}")
            true
        } else if (entry.checksum != checksum(file)) {
            println("[checksum changed] ${file.path}")
            true
        } else {
            false
        }
    }

    println("input files -> ${files.size}")
    println("files to minify -> ${filesToMinify.size}")

    if (filesToMinify.isNotEmpty()) {
        ensureMinifierInstalled()
    }

    val chunkSize = 20

    filesToMinify
        .chunked(chunkSize)
        .forEach { chunk ->
            chunk.forEach { file ->
                val result = minifyFile(file)
                println("[${file.path}] -> ${result.responseCode}")
                if (result.responseCode != 200) {
                    println("ERROR: exiting due to code ${result.responseCode}")
                    exitProcess(1)
                }
                csvEntries.removeIf { entry -> entry.filePath == file.path }
                csvEntries += (MinificationCsvEntry(file.path, checksum(file), Clock.System.now()))
            }
            // checkpoint progress so a long run doesn't have to restart from scratch
            writeCsv(csvEntries)
        }

    writeCsv(csvEntries)
}

fun main() {
    val allCss = listAllCssFiles()
    val allJs = listAllJsFiles()

    minifyIfNeeded(allCss + allJs)

    val cssUnminified = sizeUnminified(allCss)
    val cssMinified = sizeMinified(allCss)
    val jsUnminified = sizeUnminified(allJs)
    val jsMinified = sizeMinified(allJs)

    println()
    println("unminified js size -> ${formatBytes(jsUnminified)}")
    println("minified js size -> ${formatBytes(jsMinified)}")
    println("js reduction -> ${formatBytes(jsUnminified - jsMinified)}")
    println()
    println("unminified css size -> ${formatBytes(cssUnminified)}")
    println("minified css size -> ${formatBytes(cssMinified)}")
    println("css reduction -> ${formatBytes(cssUnminified - cssMinified)}")
    println()
    println("total reduction -> ${formatBytes(cssUnminified + jsUnminified - cssMinified - jsMinified)}")
    println("compression ratio -> ${(cssUnminified + jsUnminified).toDouble() / (cssMinified + jsMinified)}")
    println()
}
