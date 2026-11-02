package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.utils.ResourceUtils.resourceAsString
import io.elephantchess.webapp.rendering.resolvers.CallbackTagResolver
import kotlin.text.RegexOption.DOT_MATCHES_ALL
import kotlin.text.RegexOption.IGNORE_CASE

private val headingWithIdRegex = Regex("""<h1\b[^>]*\bid\s*=\s*"([^"]+)"[^>]*>([\s\S]*?)</h1>""", IGNORE_CASE)
private val commentedSectionRegex = Regex("""<!--\s*(.*?)\s*-->(.*?)(?=<!--|$)""", DOT_MATCHES_ALL)
private val innerTagRegex = Regex("<[^>]+>")
private val whitespaceRegex = Regex("\\s+")

fun pageTocTagResolver(
    tagName: String,
    templatePath: String,
    extractEntries: (String) -> List<Pair<String, String>> = ::headingTocEntries,
): TagResolver {
    fun renderPageToc(entries: List<Pair<String, String>>): String {
        if (entries.isEmpty()) return ""

        val items = entries.joinToString("\n") { (id, title) ->
            """<li><a href="#${escapeHtmlAttr(id)}">${escapeHtml(title)}</a></li>"""
        }
        return """<nav id="page-toc"><ul>$items</ul></nav>"""
    }

    return CallbackTagResolver(tagName) {
        val html = resourceAsString(templatePath)
            ?: throw IllegalStateException("Template not found at $templatePath")

        renderPageToc(extractEntries(html))
    }
}

fun categorizedPageTocTagResolver(tagName: String, templatePath: String): TagResolver {
    fun renderCategorizedPageToc(entries: List<Pair<String, List<Pair<String, String>>>>): String {
        if (entries.isEmpty()) return ""

        val items = entries.joinToString("\n") { (category, children) ->
            val categoryLink = children.firstOrNull()?.first
            val categoryLabel = if (categoryLink == null) {
                escapeHtml(category)
            } else {
                """<a href="#${escapeHtmlAttr(categoryLink)}">${escapeHtml(category)}</a>"""
            }
            val childItems = children.joinToString("\n") { (id, title) ->
                """<li><a href="#${escapeHtmlAttr(id)}">${escapeHtml(title)}</a></li>"""
            }
            """<li>$categoryLabel<ul>$childItems</ul></li>"""
        }
        return """<nav id="page-toc"><ul>$items</ul></nav>"""
    }

    fun commentedPageTocEntries(html: String): List<Pair<String, List<Pair<String, String>>>> =
        commentedSectionRegex.findAll(html)
            .mapNotNull { match ->
                val category = match.groupValues[1].trim()
                val entries = headingTocEntries(match.groupValues[2])
                if (category.isEmpty() || entries.isEmpty()) {
                    null
                } else {
                    category.replaceFirstChar { it.uppercase() } to entries
                }
            }
            .toList()

    return CallbackTagResolver(tagName) {
        val html = resourceAsString(templatePath)
            ?: throw IllegalStateException("Template not found at $templatePath")

        renderCategorizedPageToc(commentedPageTocEntries(html))
    }
}

private fun headingTocEntries(html: String): List<Pair<String, String>> =
    headingWithIdRegex.findAll(html)
        .map { match ->
            val id = match.groupValues[1].trim()
            val title = match.groupValues[2]
                .replace(innerTagRegex, "")
                .replace(whitespaceRegex, " ")
                .trim()

            id to title
        }
        .filter { (id, title) -> id.isNotEmpty() && title.isNotEmpty() }
        .toList()
