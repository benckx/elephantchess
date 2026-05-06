package io.elephantchess.sevenkingdoms

import io.elephantchess.sevenkingdoms.Move.Companion.parseMoveFromUci
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveTest {

    @Test
    fun parseMoveFromUciTest() {
        val moves = listOf(
            "e9c11" to Move(Position(4, 9), Position(2, 11)),
            "c0e3" to Move(Position(2, 0), Position(4, 3)),
            "k0i1" to Move(Position(10, 0), Position(8, 1)),
            "p5m8" to Move(Position(15, 5), Position(12, 8)),
            "s10p8" to Move(Position(18, 10), Position(15, 8)),
            "n15p13" to Move(Position(13, 15), Position(15, 13)),
            "f15h13" to Move(Position(5, 15), Position(7, 13)),
            "c11a13" to Move(Position(2, 11), Position(0, 13)),
            "f4f5" to Move(Position(5, 4), Position(5, 5)),
            "n4n7" to Move(Position(13, 4), Position(13, 7)),
            "m8k10" to Move(Position(12, 8), Position(10, 10)),
            "p8q6" to Move(Position(15, 8), Position(16, 6)),
            "n16p14" to Move(Position(13, 16), Position(15, 14)),
            "d17b14" to Move(Position(3, 17), Position(1, 14)),
            "a13h6" to Move(Position(0, 13), Position(7, 6)),
            "f5g4" to Move(Position(5, 5), Position(6, 4)),
            "n3n4" to Move(Position(13, 3), Position(13, 4)),
            "o5l5" to Move(Position(14, 5), Position(11, 5)),
            "r11n8" to Move(Position(17, 11), Position(13, 8)),
            "l17k15" to Move(Position(11, 17), Position(10, 15))
        )

        for ((uci, expectedMove) in moves) {
            val parsedMove = parseMoveFromUci(uci)
            assertEquals(expectedMove, parsedMove, "expected $expectedMove but got $parsedMove")
            assertEquals(uci, expectedMove.uci, "expected $uci but got ${expectedMove.uci}")
        }
    }

}
