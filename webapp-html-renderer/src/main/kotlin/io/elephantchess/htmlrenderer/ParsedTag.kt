package io.elephantchess.htmlrenderer

internal data class ParsedTag(
    val before: String,
    val tagName: String,
    val after: String,
    val iteration: Int = 1,
    val substitutions: Map<String, String> = emptyMap(),
    val keepIndentation: Boolean = true,
)
