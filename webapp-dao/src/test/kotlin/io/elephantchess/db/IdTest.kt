package io.elephantchess.db

import io.elephantchess.db.utils.generateId
import kotlin.test.Test
import kotlin.test.assertEquals

class IdTest {

    @Test
    fun logId01() {
        repeat(100) {
            println(generateId())
        }
    }

    @Test
    fun test01() {
        repeat(20) {
            assertEquals(12, generateId().length)
        }
    }

    @Test
    fun test02() {
        val ids = (1..5_000_000).map { generateId() }
        assertEquals(ids.size, ids.distinct().size)
    }

}
