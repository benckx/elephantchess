package io.elephantchess.utils

import io.elephantchess.utils.SequenceUtils.allCombinations
import kotlin.test.Test
import kotlin.test.assertEquals

class SequenceUtilsTest {

    @Test
    fun allCombinationsTest01() {
        assertEquals(setOf(setOf("a")), allCombinations(setOf("a")))
        assertEquals(setOf(setOf("a"), setOf("b"), setOf("a", "b")), allCombinations(setOf("a", "b")))
        assertEquals(
            setOf(
                setOf("a"),
                setOf("b"),
                setOf("c"),
                setOf("a", "b"),
                setOf("a", "c"),
                setOf("b", "c"),
                setOf("a", "b", "c")
            ), allCombinations(setOf("a", "b", "c"))
        )
    }

}
