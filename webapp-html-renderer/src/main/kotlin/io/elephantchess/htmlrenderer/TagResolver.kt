package io.elephantchess.htmlrenderer

interface TagResolver {

    val tagName: String

    suspend fun resolveContent(): List<String>

}
