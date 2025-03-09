package io.elephantchess.scripts

import io.elephantchess.xiangqi.Board

fun main() {
    val board = Board()
    println(board.outputFen())
    println()
    println(board.print())

    println()
    println()

    board.registerMove("h2e2") // C2=5
    board.registerMove("h9g7") // H8+7
    println(board.outputFen())
    println()
    println(board.print())
}
