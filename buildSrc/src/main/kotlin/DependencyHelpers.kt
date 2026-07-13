import org.gradle.kotlin.dsl.DependencyHandlerScope

fun DependencyHandlerScope.api(dependencyNotation: Any) =
    add("api", dependencyNotation)

fun DependencyHandlerScope.implementation(dependencyNotation: Any) =
    add("implementation", dependencyNotation)

fun DependencyHandlerScope.compileOnly(dependencyNotation: Any) =
    add("compileOnly", dependencyNotation)

fun DependencyHandlerScope.testImplementation(dependencyNotation: Any) =
    add("testImplementation", dependencyNotation)
