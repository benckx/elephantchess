package io.elephantchess.utils.di

/**
 * Marks a class to be auto-registered as a Koin singleton via classpath scanning.
 *
 * The class must have exactly one constructor; its parameters are resolved from the Koin graph.
 *
 * @param eager when true, the instance is created at startup (subject to the eagerAllowed flag passed to the scanner).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class KoinSingleton(val eager: Boolean = false)
