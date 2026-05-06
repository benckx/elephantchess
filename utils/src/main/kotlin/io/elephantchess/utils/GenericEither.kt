package io.elephantchess.utils

/**
 * Equivalent to the Scala Either.
 */
open class GenericEither<A, B>(private val a: A?, private val b: B?) {

    init {
        if ((a == null && b == null) || (a != null && b != null)) {
            throw IllegalArgumentException("Either must have one and only one value set")
        }
    }

    fun isLeft() = a != null

    fun isRight() = b != null

    fun left(): A = a!!

    fun right(): B = b!!

}
