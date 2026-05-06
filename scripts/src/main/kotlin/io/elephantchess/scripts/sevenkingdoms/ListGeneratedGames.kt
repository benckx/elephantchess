package io.elephantchess.scripts.sevenkingdoms

import io.elechantchess.sevenkingdoms.testutils.GameEntryCacheManager.getAllGames

fun main() {
    println("total games ${getAllGames().size}, total moves ${getAllGames().sumOf { it.moves.size }}")
    getAllGames().forEach { game -> game.toBoard() }

    getAllGames()
        .groupBy { it.victoryType }
        .forEach { (victoryType, games) ->
            println("$victoryType -> ${games.size}")
        }

    val countThresholdEliminationEvent = getAllGames()
        .count { game -> game.toBoard().listCaptureEvents().any { !it.generalCapture } }

    println("games with 10 pieces lost elimination event -> $countThresholdEliminationEvent")
}
