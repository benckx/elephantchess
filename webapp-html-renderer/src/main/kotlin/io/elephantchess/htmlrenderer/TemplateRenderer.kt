package io.elephantchess.htmlrenderer

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Replace tags (i.e. {{tag_name}}) in templates with their resolved content
 */
abstract class TemplateRenderer(
    private val baseTagResolvers: List<TagResolver>,
    private val disabledTemplates: List<String>,
) {

    suspend fun render(text: String, specificTagResolvers: List<TagResolver> = listOf()): String {
        val allTagResolvers = baseTagResolvers + specificTagResolvers

        return text
            .split("\n")
            .flatMap { line -> renderLine(line, allTagResolvers) }
            .joinToString("\n")
    }

    private suspend fun renderLine(line: String, resolvers: List<TagResolver>): List<String> {
        // First, inline-replace all single-value tags on this line
        val inlined = inlineResolveTags(line, resolvers)

        return when (val parsedTag = parseTag(inlined)) {
            null -> {
                // Drop lines that became blank only because a tag inlined to empty,
                // but preserve author-written blank lines (e.g. inside <pre>).
                if (inlined.isBlank() && line.isNotBlank()) listOf() else listOf(inlined)
            }
            else -> renderTag(parsedTag, resolvers)
        }
    }

    /**
     * Inline-replace tags that resolve to exactly one value, so that multiple
     * tags on a single line are supported without requiring recursion.
     */
    private suspend fun inlineResolveTags(line: String, resolvers: List<TagResolver>): String {
        var result = line
        var safety = 0
        while (safety++ < 20) {
            val tagOpen = result.indexOf(TAG_OPEN)
            val tagClose = result.indexOf(TAG_CLOSE)
            if (tagOpen < 0 || tagClose < 0 || tagClose <= tagOpen) break

            val tagName = result.substring(tagOpen + TAG_OPEN.length, tagClose)
            val resolver = resolvers.find { it.tagName == tagName }
            if (resolver != null) {
                val resolved = resolver.resolveContent()
                if (resolved.size == 1) {
                    result = result.substring(0, tagOpen) + resolved[0] + result.substring(tagClose + TAG_CLOSE.length)
                } else {
                    // Multi-line resolver — leave for block-level renderTag
                    break
                }
            } else {
                // No resolver found for this tag — skip past it to avoid infinite loop
                break
            }
        }
        return result
    }

    // Recursively resolves nested tags
    private suspend fun renderTag(parsedTag: ParsedTag, resolvers: List<TagResolver>): List<String> {
        fun isTagEnabled(tagName: String) = !disabledTemplates.contains(tagName)

        fun clearUnusedSubstitutionTags(line: String): String {
            val start = line.indexOf("\${")
            val end = line.indexOf("}")
            return if (start >= 0 && end >= 0) {
                val before = line.substring(0, start)
                val after = line.substring(end + 1)
                before + clearUnusedSubstitutionTags(after)
            } else {
                line
            }
        }

        fun applySubstitutions(line: String, i: Int): String {
            var result = line
            parsedTag.substitutions.forEach { (key, value) ->
                result = result.replace("\${$key}", value)
            }
            result = result.replace("\${i}", i.toString())
            return clearUnusedSubstitutionTags(result)
        }

        fun containsTag(line: String): Boolean {
            return line.contains(TAG_OPEN) && line.contains(TAG_CLOSE)
        }

        if (isTagEnabled(parsedTag.tagName)) {
            val resolver = resolvers.find { resolver -> resolver.tagName == parsedTag.tagName }

            if (resolver != null) {
                return (0 until parsedTag.iteration).flatMap { i ->
                    val resolvedLines = resolver.resolveContent()
                    // For multi-line content, propagating the leading indentation of the tag
                    // line to every resolved line is usually undesirable (especially inside
                    // whitespace-sensitive elements like <pre>). It can be disabled via the
                    // [[keepIndentation:false]] argument. Single-line content keeps the
                    // surrounding context as before.
                    val keepBefore = parsedTag.keepIndentation || resolvedLines.size <= 1
                    val before = if (keepBefore) parsedTag.before else ""
                    resolvedLines
                        .map { line -> applySubstitutions(line, i) }
                        .map { line -> "${before}${line}${parsedTag.after}" }
                        .flatMap { line ->
                            // Recursively render nested tags
                            if (containsTag(line)) {
                                renderLine(line, resolvers)
                            } else {
                                listOf(line)
                            }
                        }
                }
            } else {
                // Log error when tag resolver is not found
                logger.error { "Tag resolver not found for tag: {{${parsedTag.tagName}}}" }
            }
        }

        return listOf()
    }

    private fun parseTag(line: String): ParsedTag? {
        val tagOpen = line.indexOf(TAG_OPEN)
        val tagEnd = line.indexOf(TAG_CLOSE)

        return if (tagOpen >= 0 && tagEnd >= 0) {
            val beforeTag = line.substring(0, tagOpen)
            val tagName = line.substring(tagOpen + TAG_OPEN.length, tagEnd)
            val afterTag = line.substring(tagEnd + TAG_CLOSE.length)
            val parsedTag = ParsedTag(beforeTag, tagName, afterTag)
            withParsedArguments(afterTag, parsedTag)
        } else {
            null
        }
    }

    private fun withParsedArguments(afterTag: String, parsedTag: ParsedTag): ParsedTag {
        fun parseArgs(args: String): ParsedTag {
            var result = parsedTag.copy()

            args
                .split(ARG_SEPARATOR)
                .map { args -> args.trim() }
                .forEach { arg ->
                    if (arg.endsWith("x") && arg.removeSuffix("x").toIntOrNull() != null) {
                        // legacy positional iteration arg, e.g. "3x"
                        val iterator = arg.removeSuffix("x").toInt()
                        result = result.copy(iteration = iterator)
                    } else if (arg.startsWith('{') && arg.endsWith('}')) {
                        val substitutions = arg
                            .removePrefix(ARG_OPEN_SUBSTITUTION_MAP)
                            .removeSuffix(ARG_CLOSE_SUBSTITUTION_MAP)
                            .split(SUBSTITUTION_VAR_SEPARATOR)
                            .map { keyValue -> keyValue.trim().split(":") }
                            .filter { keyValueList -> keyValueList.size == 2 }
                            .map { keyValueList ->
                                keyValueList[0].trim() to keyValueList[1].trim()
                            }.associate { (key, value) ->
                                key to value
                                    .removePrefix("'")
                                    .removeSuffix("'")
                            }

                        result = result.copy(substitutions = substitutions)
                    } else if (arg.contains(KEY_VALUE_SEPARATOR)) {
                        val (key, value) = arg.split(KEY_VALUE_SEPARATOR, limit = 2)
                            .map { it.trim() }
                        when (key) {
                            "keepIndentation" -> result =
                                result.copy(keepIndentation = value.toBoolean())

                            "iterations" -> result =
                                result.copy(iteration = value.toInt())
                        }
                    }

                }

            return result
        }

        val argOpen = afterTag.indexOf(ARG_OPEN)
        val argClose = afterTag.indexOf(ARG_CLOSE)
        return if (argOpen >= 0 && argClose >= 0) {
            val args = afterTag.substring(argOpen + ARG_OPEN.length, argClose)
            val afterArgs = afterTag.substring(argClose + ARG_CLOSE.length)
            parseArgs(args).copy(after = afterArgs)
        } else {
            parsedTag
        }
    }

    private companion object {

        const val TAG_OPEN = "{{"
        const val TAG_CLOSE = "}}"
        const val ARG_OPEN = "[["
        const val ARG_CLOSE = "]]"
        const val ARG_SEPARATOR = ";"
        const val SUBSTITUTION_VAR_SEPARATOR = ","
        const val KEY_VALUE_SEPARATOR = ":"
        const val ARG_OPEN_SUBSTITUTION_MAP = "{"
        const val ARG_CLOSE_SUBSTITUTION_MAP = "}"

    }

}
