package io.elephantchess.servicelayer.services.resolvers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ResolverUtilsTest {

    @Test
    fun `makeAnchor renders anchor using html builder`() {
        val url = "https://elephantchess.io/game?id=42"
        val anchor = makeAnchor(url)
        assertEquals("""<a href="$url">$url</a>""", anchor)
        assertFalse(anchor.endsWith("\n"))
    }

}
