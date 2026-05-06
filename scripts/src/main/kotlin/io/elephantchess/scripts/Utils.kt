package io.elephantchess.scripts

import java.io.File

fun listAllJsFiles() = listAllPublicAssetsByExtension("js")

fun listAllCssFiles() = listAllPublicAssetsByExtension("css")

fun listAllPublicAssetsByExtension(ext: String, includeMinify: Boolean = false): List<File> {
    return File("webapp/src/main/resources/public/")
        .walk()
        .filter { file -> file.isFile && file.extension == ext }
        .filter { file -> includeMinify || !file.name.endsWith(".min.$ext") }
        .filter { file -> !file.path.contains("/libs/") }
        .sortedBy { file -> file.path }
        .toList()
}
