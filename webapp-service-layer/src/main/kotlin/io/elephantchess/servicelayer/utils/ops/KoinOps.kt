package io.elephantchess.servicelayer.utils.ops

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.java.KoinJavaComponent.inject
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.jvmErasure

private val kLogger = logger {}

inline fun <reified T : Any> Module.singleAuto(eager: Boolean = false): KoinDefinition<T> {
    return single(createdAtStart = eager) { reflectiveResolver(T::class) }
}

fun <T : Any> Scope.reflectiveResolver(klass: KClass<T>): T {
    val constructors = klass.constructors
    if (constructors.size != 1) {
        throw IllegalArgumentException("class ${klass.simpleName} must have exactly one constructor")
    }
    val constructor = constructors.first()
    val args = constructor.parameters
        .map { param -> param.type.jvmErasure }
        .map { paramClass ->
            if (paramClass.simpleName == "KLogger") {
                logger(klass.qualifiedName!!)
            } else {
                kLogger.info { "inserting ${paramClass.simpleName} into ${klass.simpleName}" }
                get(paramClass, null, null) as Any
            }
        }

    return constructor.call(*args.toTypedArray())
}

inline fun <reified T> getKoinInstance(): T {
    val component = object : KoinComponent {
        val value: T by inject(T::class.java)
    }

    return component.value
}

inline fun <reified T> getKoinInstance(name: String): T {
    val component = object : KoinComponent {
        val value: T by inject(T::class.java, named(name))
    }

    return component.value
}

/**
 * Property delegate for lazy Koin dependency injection.
 * Doesn't require to extend KoinComponent (can be used in a top level function)
 * Usage: private val service by koin<MyService>()
 */
inline fun <reified T> koin(): ReadOnlyProperty<Any?, T> = KoinDelegate { getKoinInstance<T>() }

/**
 * Property delegate for lazy Koin dependency injection with named qualifier.
 * Doesn't require to extend KoinComponent (can be used in a top level function)
 * Usage: private val service by koin<MyService>("myName")
 */
inline fun <reified T> koin(name: String): ReadOnlyProperty<Any?, T> = KoinDelegate { getKoinInstance<T>(name) }

class KoinDelegate<T>(initializer: () -> T) : ReadOnlyProperty<Any?, T> {
    private val lazy = lazy(initializer)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = lazy.value
}
