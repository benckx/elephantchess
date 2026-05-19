package io.elephantchess.servicelayer.services.resolvers

import kotlin.test.Test
import kotlin.test.assertEquals

class ResolverUtilsTest {

    @Test
    fun `makeAnchor renders anchor using html builder`() {
        val url = "https://elephantchess.io/game?id=42"
        assertEquals("""<a href="$url">$url</a>""", makeAnchor(url))
    }

}
