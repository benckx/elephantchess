package io.elephantchess.utils

/**
 * Result of a call that may throw an Exception.
 * Either holds an Exception, either the expected return value.
 */
open class TryEither<T>(e: Exception?, t: T?) : GenericEither<Exception, T>(e, t) {

    class Invalid<T>(e: Exception) : TryEither<T>(e, null) {

        val exception: Exception
            get() = left()

    }

    class Valid<B>(b: B) : TryEither<B>(null, b) {

        val value: B
            get() = right()

    }

}
