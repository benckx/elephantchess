package io.elephantchess.xiangqi

enum class JanggiSetup(val fen: String) {

    INNER_ELEPHANT("rnba1abnr"),
    OUTER_ELEPHANT("rbna1anbr"),
    RIGHT_ELEPHANT("rnba1anbr"),
    LEFT_ELEPHANT("rbna1abnr"),
    CENTRAL_CHARIOT("bnra1arnb"),
}

fun janggiStartFen(blue: JanggiSetup = JanggiSetup.INNER_ELEPHANT, red: JanggiSetup = JanggiSetup.INNER_ELEPHANT): String =
    "${red.fen}/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/${blue.fen.uppercase()} w - - 0 1"

fun janggiStartFen(blueSetup: String, redSetup: String): String =
    janggiStartFen(JanggiSetup.valueOf(blueSetup), JanggiSetup.valueOf(redSetup))
