package io.elephantchess.servicelayer.utils.ops

import io.elephantchess.utils.di.KoinSingleton
import io.github.classgraph.ClassGraph
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.component.KoinComponent
import org.koin.core.definition.BeanDefinition
import org.koin.core.definition.Kind
import org.koin.core.instance.SingleInstanceFactory
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.java.KoinJavaComponent.inject
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.jvmErasure

private val kLogger = logger {}

/**
 * Scans [packages] for classes annotated with [KoinSingleton] and registers each as a Koin singleton,
 * resolving constructor dependencies reflectively (see [reflectiveResolver]).
 *
 * A class annotated with `@Service(eager = true)` is created at startup only when [eagerAllowed] is true.
 */
fun Module.registerInjectables(eagerAllowed: Boolean, vararg packages: String) {
    ClassGraph()
        .enableAnnotationInfo()
        .acceptPackages(*packages)
        .scan()
        .use { scanResult ->
            scanResult.getClassesWithAnnotation(KoinSingleton::class.java.name).forEach { classInfo ->
                @Suppress("UNCHECKED_CAST")
                val klass = classInfo.loadClass().kotlin as KClass<Any>
                val eager = (classInfo
                    .getAnnotationInfo(KoinSingleton::class.java.name)
                    ?.parameterValues
                    ?.getValue("eager") as? Boolean) ?: false
                kLogger.info { "registering injectable ${klass.simpleName} (eager=$eager)" }
                registerInjectable(klass, createdAtStart = eager && eagerAllowed)
            }
        }
}

private fun Module.registerInjectable(klass: KClass<Any>, createdAtStart: Boolean) {
    val beanDefinition = BeanDefinition(
        scopeQualifier = named("_root_"),
        primaryType = klass,
        qualifier = null,
        definition = { reflectiveResolver(klass) },
        kind = Kind.Singleton,
        secondaryTypes = emptyList(),
    )
    val factory = SingleInstanceFactory(beanDefinition)
    @OptIn(KoinInternalApi::class)
    indexPrimaryType(factory)
    if (createdAtStart) {
        @OptIn(KoinInternalApi::class)
        prepareForCreationAtStart(factory)
    }
}

private fun <T : Any> Scope.reflectiveResolver(klass: KClass<T>): T {
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
