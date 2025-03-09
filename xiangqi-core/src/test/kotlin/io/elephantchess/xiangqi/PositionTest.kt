package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.Position.Companion.parsePositionFromUci
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PositionTest {

    @Test
    fun parsePositionFromUciTest01() {
        assertEquals(Position(0, 0), parsePositionFromUci("a0"))
        assertEquals(Position(0, 1), parsePositionFromUci("a1"))
        assertEquals(Position(1, 1), parsePositionFromUci("b1"))
        assertEquals(Position(7, 8), parsePositionFromUci("h8"))

        assertFailsWith<IllegalArgumentException> { parsePositionFromUci("h88") }
        assertFailsWith<IllegalArgumentException> { parsePositionFromUci("x5") }
    }

}
