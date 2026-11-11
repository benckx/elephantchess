package io.elephantchess.scripts.janggi

import io.elephantchess.xiangqi.janggiStartFen
import io.elephantchess.xiangqi.testutils.JanggiGameMovesDto

fun JanggiGameMovesDto.startFen(): String =
    janggiStartFen(blueSetup, redSetup)
