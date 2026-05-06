package io.elephantchess.sevenkingdoms

import io.elephantchess.sevenkingdoms.Color.*
import io.elephantchess.sevenkingdoms.Color.Companion.areContiguous
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorTest {

    @Test
    fun testAreContiguous() {
        assertTrue(areContiguous(listOf(WHITE, RED, ORANGE)))
        assertTrue(areContiguous(listOf(BLUE, GREEN, PURPLE, BLACK, WHITE)))
        assertTrue(areContiguous(listOf(RED, ORANGE, BLUE)))
        assertTrue(areContiguous(listOf(GREEN, PURPLE, BLACK, WHITE, RED)))
        assertTrue(areContiguous(listOf(ORANGE, BLUE, GREEN)))
        assertFalse(areContiguous(listOf(WHITE, BLUE, RED)))
        assertFalse(areContiguous(listOf(WHITE, BLACK)))
        assertFalse(areContiguous(listOf(RED, GREEN)))
        assertFalse(areContiguous(listOf(BLUE, ORANGE, WHITE)))
        assertFalse(areContiguous(listOf(PURPLE, RED, GREEN)))
        assertFalse(areContiguous(listOf(BLACK, WHITE, BLUE)))
        assertFalse(areContiguous(listOf(RED)))
        assertFalse(areContiguous(emptyList()))
    }

}
