package io.elephantchess.scripts.minification

import io.elephantchess.scripts.listAllCssFiles
import io.elephantchess.scripts.listAllJsFiles
import java.io.File
import kotlin.system.exitProcess

/**
 * Local Node project that minifies assets without any network round-trip:
 * JavaScript with SWC (https://swc.rs, the same engine the previously used Toptal
 * endpoint is based on) and CSS with Lightning CSS (https://lightningcss.dev).
 */
private val MINIFIER_DIR = File("scripts/minifier")
private val MINIFIER_SCRIPT = File(MINIFIER_DIR, "minify.mjs")

private val IS_WINDOWS = System.getProperty("os.name").orEmpty().lowercase().contains("win")

/**
 * Resolves the absolute path of a Node executable (`node` or `npm`).
 *
 * [ProcessBuilder] only looks at the JVM process `PATH`, which frequently omits
 * version-manager (nvm, fnm, asdf) and Homebrew directories — for example when the
 * task is launched from an IDE rather than an interactive shell. We therefore search
 * the `PATH` plus those common install locations, honor an explicit `NODE_BIN` /
 * `NPM_BIN` override, and account for the `.cmd` wrappers used on Windows.
 *
 * Falls back to the bare name (letting the OS resolve it) when nothing is found, so
 * setups that already work keep working.
 */
private fun resolveNodeExecutable(name: String): String {
    System.getenv("${name.uppercase()}_BIN")
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val candidateNames = if (IS_WINDOWS) listOf("$name.cmd", "$name.exe", name) else listOf(name)

    val home = System.getProperty("user.home").orEmpty()
    val pathDirs = System.getenv("PATH").orEmpty()
        .split(File.pathSeparatorChar)
        .filter { it.isNotBlank() }
        .map { File(it) }

    val versionManagerBinDirs = listOf(
        File(home, ".nvm/versions/node"),
        File(home, ".local/share/fnm/node-versions"),
        File(home, ".asdf/installs/nodejs"),
    ).flatMap { root -> root.listFiles()?.toList().orEmpty() }
        .filter { it.isDirectory }
        .flatMap { version -> listOf(File(version, "bin"), File(version, "installation/bin")) }

    val commonDirs = if (IS_WINDOWS) {
        emptyList()
    } else {
        listOf(
            File("/usr/local/bin"),
            File("/opt/homebrew/bin"),
            File("/usr/bin"),
            File(home, ".volta/bin"),
        )
    }

    (pathDirs + versionManagerBinDirs + commonDirs).forEach { dir ->
        candidateNames.forEach { candidate ->
            val file = File(dir, candidate)
            if (file.isFile && file.canExecute()) {
                return file.absolutePath
            }
        }
    }

    return candidateNames.first()
}

private val NODE_EXECUTABLE by lazy { resolveNodeExecutable("node") }
private val NPM_EXECUTABLE by lazy { resolveNodeExecutable("npm") }

private data class MinifyResult(
    val responseCode: Int,
)

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
        ProcessBuilder(NPM_EXECUTABLE, "install")
            .directory(MINIFIER_DIR)
            .inheritIO()
            .start()
            .waitFor()
    } catch (e: java.io.IOException) {
        println(
            "ERROR: failed to start '$NPM_EXECUTABLE' (is Node.js/npm installed and on the PATH?): ${e.message}. " +
                "If npm is installed in a non-standard location, set the NPM_BIN environment variable to its full path.",
        )
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
        ProcessBuilder(NODE_EXECUTABLE, MINIFIER_SCRIPT.path, type).start()
    } catch (e: java.io.IOException) {
        println(
            "ERROR: failed to start '$NODE_EXECUTABLE' (is Node.js installed and on the PATH?): ${e.message}. " +
                "If node is installed in a non-standard location, set the NODE_BIN environment variable to its full path.",
        )
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
 * Minifies all the given files, overwriting any existing minified counterparts.
 */
fun minifyAll(files: List<File>) {
    files.forEach { file -> println("[found] ${file.path}") }

    println("files to minify -> ${files.size}")

    if (files.isEmpty()) {
        return
    }

    ensureMinifierInstalled()

    files.forEach { file ->
        val result = minifyFile(file)
        println("[${file.path}] -> ${result.responseCode}")
        if (result.responseCode != 200) {
            println("ERROR: exiting due to code ${result.responseCode}")
            exitProcess(1)
        }
    }
}

fun main() {
    val allCss = listAllCssFiles()
    val allJs = listAllJsFiles()

    minifyAll(allCss + allJs)

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
