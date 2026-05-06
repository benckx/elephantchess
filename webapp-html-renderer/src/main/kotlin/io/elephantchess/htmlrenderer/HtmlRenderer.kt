package io.elephantchess.htmlrenderer

import io.elephantchess.utils.ResourceUtils.resourceAsString
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag

/**
 * On top of [TemplateRenderer], this adds:
 * - formatting of head tags
 * - pointing to minified CSS and JS files
 * - minification of the HTML (removal of comments and blank lines)
 */
class HtmlRenderer(
    private val isMinificationEnabled: Boolean,
    private val cdnFolder: String?,
    private val webTemplateRenderer: TemplateRenderer,
    private val logger: KLogger = logger {},
) {

    init {
        logger.info { "CDN folder: $cdnFolder" }
    }

    suspend fun renderHtml(
        templatePath: String,
        specificTagResolvers: List<TagResolver>,
        skipDocType: Boolean = false
    ): String {
        logger.debug {
            if (specificTagResolvers.isEmpty()) {
                "rendering html template $templatePath"
            } else {
                "rendering html template $templatePath with tag resolvers ${specificTagResolvers.joinToString(", ")}"
            }
        }

        val rendered = resourceAsString(templatePath)
            ?.let { htmlTemplate -> webTemplateRenderer.render(htmlTemplate, specificTagResolvers) }
            ?.let { html -> renderHead(html) }
            ?.let { html -> updateCssAssetLocations(html) }
            ?.let { html -> updateJsAssetLocations(html) }
            ?.let { html -> updateImageAssetLocations(html) }
            ?.let { html -> removeHtmlComments(html) }
            ?.let { html -> removeBlankLines(html) }
            ?.let { html -> if (!skipDocType) ensureDoctype(html) else html }

        if (rendered == null) {
            logger.warn { "failed to render html template $templatePath" }
        }

        return rendered ?: "failed to render html template $templatePath"
    }

    /**
     * Add missing meta tags and base CSS files to the head of the HTML content
     */
    private fun renderHead(htmlContent: String): String {
        fun sortElements(head: Element): Element {
            val elements = head.allElements.toList()
            val output = mutableListOf<Element>()
            val order = listOf("title", "meta", "link", "style", "script")
            order.forEach { tagName ->
                output += elements.filter { it.tagName() == tagName }
            }

            val newHead = Element(Tag.valueOf("head"), "")
            output.forEach { newHead.appendChild(it) }
            return newHead
        }

        fun reInsertHead(head: Element): String {
            var headOpened = false
            val lines = mutableListOf<String>()
            htmlContent.split("\n").forEach { line ->
                if (line.trim() == "<head>") {
                    headOpened = true
                    lines += head.outerHtml()
                }
                if (!headOpened) {
                    lines += line
                }
                if (line.trim() == "</head>") {
                    headOpened = false
                }
            }

            return lines.joinToString("\n")
        }

        val description = resourceAsString("/web_fragments/head_description.txt")!!.trim()
        val keywords = resourceAsString("/web_fragments/head_keywords.txt")!!.trim().split("\n").joinToString(", ")

        val document = Jsoup.parse(htmlContent)
        val head = document.head()

        val links = head.getElementsByTag("link").toList()
        val metas = head.getElementsByTag("meta").toList()
        val elementsToAdd = mutableListOf<Element>()

        if (!links.hasAttribute("rel", "icon")) {
            val link = document.createElement("link")
            link.attr("rel", "icon")
            link.attr("type", "image/x-icon")
            link.attr("href", "/favicon.ico")
            elementsToAdd.add(link)
        }

        if (!metas.hasAttribute("name", "description")) {
            elementsToAdd.add(document.createNameMetaElement("description", description))
        }

        if (!metas.hasAttribute("name", "keywords")) {
            elementsToAdd.add(document.createNameMetaElement("keywords", keywords))
        }

        if (!metas.hasAttribute("name", "author")) {
            elementsToAdd.add(document.createNameMetaElement("author", "benckx"))
        }

        if (!metas.hasAttribute("charset")) {
            val meta = document.createElement("meta")
            meta.attr("charset", "UTF-8")
            elementsToAdd.add(meta)
        }

        head.prependChildren(elementsToAdd)
        return reInsertHead(sortElements(head))
    }

    private fun removeHtmlComments(htmlContent: String) =
        if (isMinificationEnabled) {
            htmlContent.replace("<!--.*?-->".toRegex(), "")
        } else {
            htmlContent
        }

    private fun removeBlankLines(htmlContent: String) =
        if (isMinificationEnabled) {
            htmlContent
                .split("\n")
                .filter { it.isNotBlank() }
                .joinToString("\n")
        } else {
            htmlContent
        }

    private fun ensureDoctype(htmlContent: String): String {
        val trimmed = htmlContent.trimStart()
        return if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true)) {
            htmlContent
        } else {
            "<!DOCTYPE html>\n$htmlContent"
        }
    }

    private fun updateJsAssetLocations(htmlContent: String) =
        updateAssetLocations(
            htmlContent = htmlContent,
            folder = "js",
            tagName = "script",
            sourceAttr = "src",
            canBeMinified = true
        )

    private fun updateCssAssetLocations(htmlContent: String) =
        updateAssetLocations(
            htmlContent = htmlContent,
            folder = "css",
            tagName = "link",
            sourceAttr = "href",
            canBeMinified = true
        )

    private fun updateImageAssetLocations(htmlContent: String) =
        updateAssetLocations(
            htmlContent = htmlContent,
            folder = "images",
            tagName = "img",
            sourceAttr = "src",
            canBeMinified = false
        )

    /**
     * Replace by the minified version or the CDN location
     */
    private fun updateAssetLocations(
        htmlContent: String,
        folder: String,
        tagName: String,
        sourceAttr: String,
        canBeMinified: Boolean
    ): String {
        return if ((canBeMinified && isMinificationEnabled) || cdnFolder != null) {
            val document = Jsoup.parse(htmlContent)
            val elements = document.getElementsByTag(tagName)

            elements.forEach { element ->
                val filePath = element.attr(sourceAttr)
                val mustUpdateAssetLocation = filePath.isNotEmpty() &&
                        filePath.startsWith("/$folder") &&
                        !filePath.contains("/libs/")

                if (mustUpdateAssetLocation) {
                    val ext = filePath.split(".").last()
                    val termination = if (canBeMinified) ".min.$ext" else ".$ext"
                    val newFileName = filePath.split("/").last().split(".").first() + termination
                    val filePathBeforeName = filePath.split("/").dropLast(1).joinToString("/")
                    val sourceAttrValue =
                        if (cdnFolder != null) {
                            "$CDN_BASE/$cdnFolder$filePathBeforeName/$newFileName"
                        } else {
                            "$filePathBeforeName/$newFileName"
                        }

                    element.attr(sourceAttr, sourceAttrValue)
                }
            }

            document.outerHtml()
        } else {
            htmlContent
        }
    }

    companion object {

        const val CDN_BASE = "https://cdn.elephantchess.io"

        private fun List<Element>.hasAttribute(attrName: String): Boolean {
            return this.find { it.hasAttr(attrName) } != null
        }

        private fun List<Element>.hasAttribute(attrName: String, attrValue: String): Boolean {
            return this.find { it.attr(attrName) == attrValue } != null
        }

        private fun Document.createNameMetaElement(name: String, content: String): Element {
            val meta = createElement("meta")
            meta.attr("name", name)
            meta.attr("content", content)
            return meta
        }

    }

}
