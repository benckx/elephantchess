package io.elephantchess.scripts.minification

import io.elephantchess.scripts.listAllCssFiles
import io.elephantchess.scripts.listAllJsFiles
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.Instant

const val CSS_API = "https://www.toptal.com/developers/cssminifier/api/raw"

const val CSV_FILE = "minified_files.csv"

/**
 * Local Node project that minifies JavaScript with SWC (https://swc.rs), the same
 * engine the Toptal endpoint is based on. JS is minified locally to avoid the
 * rate-limited network round-trip; CSS still uses the remote [CSS_API].
 */
private val SWC_MINIFIER_DIR = File("scripts/swc-minifier")
private val SWC_MINIFIER_SCRIPT = File(SWC_MINIFIER_DIR, "minify.mjs")

private data class MinifyResult(
    val responseCode: Int,
    val usedRemoteApi: Boolean,
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
        "js" -> minifyJsWithSwc(file, outputFile)
        "css" -> minifyCssWithRemoteApi(file, outputFile)
        else -> throw IllegalArgumentException("unsupported extension ${file.extension}")
    }
}

/**
 * Ensures the local SWC minifier dependencies are installed (runs `npm install`
 * the first time, when `node_modules` is missing).
 */
private fun ensureSwcMinifierInstalled() {
    if (File(SWC_MINIFIER_DIR, "node_modules/@swc/core").exists()) {
        return
    }

    println("[swc] installing minifier dependencies in ${SWC_MINIFIER_DIR.path} ...")
    val exitCode = ProcessBuilder("npm", "install")
        .directory(SWC_MINIFIER_DIR)
        .inheritIO()
        .start()
        .waitFor()

    if (exitCode != 0) {
        println("ERROR: 'npm install' failed with code $exitCode")
        exitProcess(1)
    }
}

/**
 * Minifies a JavaScript file locally with SWC by piping its content through the
 * Node [SWC_MINIFIER_SCRIPT]. Returns code 200 on success to match the remote flow.
 */
private fun minifyJsWithSwc(file: File, outputFile: File): MinifyResult {
    val process = ProcessBuilder("node", SWC_MINIFIER_SCRIPT.path).start()

    process.outputStream.use { stdin -> stdin.write(file.readBytes()) }

    val output = process.inputStream.readBytes().decodeToString()
    val errors = process.errorStream.readBytes().decodeToString()
    val exitCode = process.waitFor()

    if (exitCode == 0) {
        outputFile.writeText(output)
        return MinifyResult(200, usedRemoteApi = false)
    }

    println("[swc] failed to minify ${file.path}: $errors")
    return MinifyResult(500, usedRemoteApi = false)
}

// https://www.toptal.com/developers/cssminifier/documentation
private fun minifyCssWithRemoteApi(file: File, outputFile: File): MinifyResult {
    val input = file.readLines().joinToString("\n")

    // content
    val content = StringBuilder().apply {
        append(URLEncoder.encode("input", "UTF-8"))
        append("=")
        append(URLEncoder.encode(input, "UTF-8"))
    }.toString()

    // request
    val request =
        (URI(CSS_API).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("charset", "utf-8")
            setRequestProperty("Content-Length", content.length.toString())
            OutputStreamWriter(outputStream).apply {
                write(content)
                flush()
            }
        }

    // persist response
    if (request.responseCode == 200) {
        val output = InputStreamReader(request.inputStream).readText()
        outputFile.writeText(output)
    }

    return MinifyResult(request.responseCode, usedRemoteApi = true)
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

    if (filesToMinify.any { file -> file.extension == "js" }) {
        ensureSwcMinifierInstalled()
    }

    val chunkSize = 20
    var processed = 0

    filesToMinify
        .chunked(chunkSize)
        .forEach { chunk ->
            var chunkUsedRemoteApi = false
            chunk.forEach { file ->
                val result = minifyFile(file)
                println("[${file.path}] -> ${result.responseCode}")
                if (result.responseCode != 200) {
                    println("ERROR: exiting due to code ${result.responseCode}")
                    exitProcess(1)
                }
                if (result.usedRemoteApi) {
                    chunkUsedRemoteApi = true
                }
                csvEntries.removeIf { entry -> entry.filePath == file.path }
                csvEntries += (MinificationCsvEntry(file.path, checksum(file), Clock.System.now()))
                processed++
            }
            if (chunk.size == chunkSize && chunkUsedRemoteApi) {
                writeCsv(csvEntries)
                val toWait = 90_000L
                println("sleeping rate limit for $toWait ms., remaining ${filesToMinify.size - processed}")
                Thread.sleep(toWait)
            }
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
