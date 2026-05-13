package io.elephantchess.webapp.rendering

/**
 * HTML-escapes a string so it can be safely inlined as text content.
 */
fun escapeHtml(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

/**
 * HTML-escapes a string so it can be safely used as an attribute value.
 */
fun escapeHtmlAttr(s: String): String =
    escapeHtml(s).replace("\"", "&quot;")
