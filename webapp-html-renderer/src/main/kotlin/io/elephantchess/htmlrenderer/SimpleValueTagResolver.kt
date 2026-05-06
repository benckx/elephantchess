package io.elephantchess.htmlrenderer

class SimpleValueTagResolver(override val tagName: String, private val value: String) : TagResolver {

    override suspend fun resolveContent() = listOf(value)
    override fun toString() = "$tagName '$value'"

}
