package io.elephantchess.htmlrenderer

import io.elephantchess.utils.ResourceUtils.resourceAsString

abstract class ResourceTagResolver(override val tagName: String) : TagResolver {

    abstract fun resolvePath(tagName: String): String

    override suspend fun resolveContent(): List<String> =
        when (val content = resourceAsString(resolvePath(tagName))) {
            null -> listOf()
            // Preserve blank lines (e.g. inside <pre> blocks), but strip the trailing
            // empty entry produced by a file's final newline.
            else -> content.removeSuffix("\n").split("\n")
        }

}
