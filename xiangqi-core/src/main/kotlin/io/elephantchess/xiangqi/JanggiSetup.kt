package io.elephantchess.xiangqi

enum class JanggiSetup(val fen: String) {

    INNER_ELEPHANT("rnba1abnr"),
    OUTER_ELEPHANT("rbna1anbr"),
    RIGHT_ELEPHANT("rnba1anbr"),
    LEFT_ELEPHANT("rbna1abnr"),
    CENTRAL_CHARIOT("bnra1arnb"),
}

/** Blue plays first from the uppercase bottom rank; Red occupies the lowercase top rank. */
fun generateJanggiStartFen(blue: JanggiSetup = JanggiSetup.OUTER_ELEPHANT, red: JanggiSetup = JanggiSetup.OUTER_ELEPHANT): String =
    "${red.fen}/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/${blue.fen.uppercase()} w - - 0 1"
