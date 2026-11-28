package io.elephantchess.xiangqi

import io.elephantchess.utils.StringUtils.capitalize

/**
 * The chess variant being played.
 */
enum class Variant {

    XIANGQI,

    MANCHU,

    JANGGI,

    ;

    fun prettyName(): String =
        name.capitalize()
}
