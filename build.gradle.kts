import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.gradle.jvm.tasks.Jar
import org.h2.Driver
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Target
import java.sql.Connection

fun DependencyHandlerScope.api(dependencyNotation: Any) = add("api", dependencyNotation)

fun DependencyHandlerScope.implementation(dependencyNotation: Any) = add("implementation", dependencyNotation)

fun DependencyHandlerScope.testImplementation(dependencyNotation: Any) = add("testImplementation", dependencyNotation)

val rootLibs = libs

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.jooq.codegen)
        classpath(libs.h2)
        classpath(libs.liquibase.core)
    }
}

plugins {
    alias(libs.plugins.versions)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.test.logger) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.adarshr.test-logger")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    repositories {
        mavenCentral()
        mavenLocal()
    }

    dependencies {
        implementation(rootLibs.kotlin.logging)
        implementation(rootLibs.kotlin.stdlib)
        implementation(rootLibs.coroutines.core)
        implementation(rootLibs.logback.classic)
        testImplementation(rootLibs.kotlin.test)
        testImplementation(rootLibs.coroutines.test)
        testImplementation(rootLibs.mockito.kotlin)
        testImplementation(rootLibs.junit.jupiter.params)
    }

    val nettyVersion = "4.2.12.Final"
    configurations.configureEach {
        resolutionStrategy {
            force("org.apache.commons:commons-lang3:${rootLibs.versions.commonsLang3.get()}")
            force("org.checkerframework:checker-qual:${rootLibs.versions.checkerQual.get()}")
            force("io.netty:netty-buffer:$nettyVersion")
            force("io.netty:netty-codec:$nettyVersion")
            force("io.netty:netty-codec-base:$nettyVersion")
            force("io.netty:netty-codec-compression:$nettyVersion")
            force("io.netty:netty-codec-dns:$nettyVersion")
            force("io.netty:netty-codec-http:$nettyVersion")
            force("io.netty:netty-codec-http2:$nettyVersion")
            force("io.netty:netty-codec-socks:$nettyVersion")
            force("io.netty:netty-common:$nettyVersion")
            force("io.netty:netty-handler:$nettyVersion")
            force("io.netty:netty-handler-proxy:$nettyVersion")
            force("io.netty:netty-resolver:$nettyVersion")
            force("io.netty:netty-resolver-dns:$nettyVersion")
            force("io.netty:netty-transport:$nettyVersion")
            force("io.netty:netty-transport-classes-epoll:$nettyVersion")
            force("io.netty:netty-transport-classes-kqueue:$nettyVersion")
            force("io.netty:netty-transport-native-epoll:$nettyVersion")
            force("io.netty:netty-transport-native-kqueue:$nettyVersion")
            force("io.netty:netty-transport-native-unix-common:$nettyVersion")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        minHeapSize = "1G"
        maxHeapSize = "1G"
        failOnNoDiscoveredTests = false
    }
}

val daoCodeGen = tasks.register("dao-code-gen") {
    doLast {
        val conn: Connection = Driver().connect("jdbc:h2:mem:test", null)

        conn.createStatement().use { stmt ->
            stmt.execute("drop all OBJECTS")
            stmt.execute("set schema PUBLIC")
        }

        val resourceAccessor = DirectoryResourceAccessor(
            project.layout.projectDirectory.dir("webapp-dao-migration/src/main/resources").asFile,
        )
        val db = H2Database().apply {
            setConnection(JdbcConnection(conn))
        }

        val liquibase = Liquibase("liquibase-changelog-generation.xml", resourceAccessor, db)
        liquibase.update(Contexts())
        conn.commit()

        val jdbc = Jdbc()
            .withDriver("org.h2.Driver")
            .withUrl("jdbc:h2:mem:test")

        val forcedTypeMap = linkedMapOf<String, String>()
        file("jooq-type-mappings.txt").forEachLine { line ->
            val parts = line.split(",")
            val columnName = parts[0].trim()
            val typeName = parts[1].trim()
            forcedTypeMap[columnName] = typeName
        }

        val forcedTypes = forcedTypeMap
            .map { (key, value) ->
                ForcedType()
                    .withIncludeExpression(key)
                    .withUserType(value)
                    .withEnumConverter(true)
            }
            .toMutableList()

        forcedTypes += ForcedType()
            .withIncludeTypes("(?i:TIMESTAMP\\s+WITH\\s+TIME\\s+ZONE|TIMESTAMP\\([0-9]+\\)\\s+WITH\\s+TIME\\s+ZONE|TIMESTAMPTZ)")
            .withUserType("kotlin.time.Instant")
            .withConverter("io.elephantchess.db.codegen.OffsetDateTimeInstantConverter")

        val database = Database()
            .withExcludes("DATABASECHANGELOG|DATABASECHANGELOGLOCK")
            .withInputSchema("PUBLIC")
            .withForcedTypes(forcedTypes)

        val generate = Generate()
            .withPojos(true)
            .withDaos(true)

        val target = Target()
            .withPackageName("io.elephantchess.db.dao.codegen")
            .withDirectory("${project.projectDir}/webapp-dao/build/jooq")

        val generator = Generator()
            .withDatabase(database)
            .withGenerate(generate)
            .withTarget(target)

        GenerationTool.generate(
            Configuration()
                .withJdbc(jdbc)
                .withGenerator(generator),
        )

        conn.close()
    }
}

project(":webapp-dao").tasks.named("compileKotlin") {
    dependsOn(daoCodeGen)
}

val publishableModules = listOf(
    "engine-api",
    "xiangqi-core",
    "xiangqi-core-test-utils",
    "seven-kingdoms-core",
    "seven-kingdoms-core-test-utils",
)

configure(publishableModules.map { project(":$it") }) {
    apply(plugin = "maven-publish")

    group = "io.elephantchess"
    version = rootProject.findProperty("publishVersion") as String? ?: "1.0.0-SNAPSHOT"

    extensions.configure<JavaPluginExtension> {
        withJavadocJar()
        withSourcesJar()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}

project(":engine-api") {
    dependencies {
        implementation(rootLibs.coroutines.core)
        implementation(project(":xiangqi-core"))
        testImplementation(project(":xiangqi-core"))
    }
}

project(":xiangqi-core") {
    dependencies {
        testImplementation(project(":xiangqi-core-test-utils"))
    }
}

project(":seven-kingdoms-core") {
    dependencies {
        testImplementation(project(":seven-kingdoms-core-test-utils"))
    }
}

project(":seven-kingdoms-core-test-utils") {
    dependencies {
        implementation(project(":seven-kingdoms-core"))
        api(rootLibs.jackson.datatype.jsr310)
        api(rootLibs.jackson.module.kotlin)
    }
}

project(":webapp-dao-migration") {
    dependencies {
        implementation(rootLibs.liquibase.core)
        implementation(rootLibs.postgresql)
    }
}

project(":webapp-dao") {
    the<SourceSetContainer>()["main"].java.srcDir(layout.buildDirectory.dir("jooq"))

    dependencies {
        implementation(project(":utils"))
        implementation(project(":webapp-dao-migration"))
        implementation(project(":seven-kingdoms-core"))
        implementation(project(":webapp-config"))
        implementation(project(":webapp-model-common"))
        implementation(project(":xiangqi-core"))
        implementation(project(":engine-api"))
        implementation(rootLibs.jooq)
        implementation(rootLibs.jooq.kotlin)
        implementation(rootLibs.jooq.kotlin.coroutines)
        implementation(rootLibs.liquibase.core)
        implementation(rootLibs.r2dbc.postgresql)
        implementation(rootLibs.r2dbc.pool)
    }
}

project(":webapp-html-renderer") {
    dependencies {
        implementation(project(":utils"))
        implementation(rootLibs.ktor.server.html.builder)
        implementation(rootLibs.jsoup)
    }
}

project(":webapp-service-layer") {
    dependencies {
        api(project(":utils"))
        api(project(":webapp-config"))
        api(project(":webapp-model-common"))
        api(project(":webapp-html-renderer"))
        implementation(project(":seven-kingdoms-core"))
        implementation(project(":webapp-dao"))
        api(rootLibs.koin.core)
        api(project(":xiangqi-core"))
        api(project(":engine-api"))
        api(rootLibs.commons.lang3)
        implementation(rootLibs.jsoup)
        implementation(rootLibs.commons.validator)
        implementation(rootLibs.java.jwt)
        implementation(rootLibs.javax.mail)
        implementation(rootLibs.cache4k)
        implementation(rootLibs.kubernetes.client)
        implementation(rootLibs.jooq)
        implementation(rootLibs.ktor.client.core)
        implementation(rootLibs.ktor.client.cio)
        implementation(rootLibs.ktor.client.content.negotiation)
        implementation(rootLibs.ktor.client.logging)
        implementation(rootLibs.ktor.serialization.jackson)
        implementation(rootLibs.ktor.serialization.kotlinx.json)
        implementation(rootLibs.ktor.server.html.builder)
        testImplementation(project(":xiangqi-core-test-utils"))
        testImplementation(project(":seven-kingdoms-core-test-utils"))
        testImplementation(rootLibs.commons.rng.simple)
        testImplementation(rootLibs.commons.text)
        testImplementation(rootLibs.h2)
        testImplementation(rootLibs.liquibase.core)
        testImplementation(rootLibs.testcontainers.postgresql)
    }
}

project(":webapp-config") {
    dependencies {
        implementation(rootLibs.commons.cli)
    }
}

project(":webapp") {
    dependencies {
        implementation(project(":webapp-service-layer"))
        implementation(rootLibs.ktor.server.core)
        implementation(rootLibs.ktor.server.netty)
        implementation(rootLibs.ktor.server.status.pages)
        implementation(rootLibs.ktor.server.default.headers)
        implementation(rootLibs.ktor.server.content.negotiation)
        implementation(rootLibs.ktor.server.caching.headers)
        implementation(rootLibs.ktor.server.html.builder)
        implementation(rootLibs.ktor.serialization.jackson)
        implementation(rootLibs.ktor.serialization.kotlinx.json)
        implementation(rootLibs.ktor.server.websockets)
        implementation(rootLibs.ktor.server.compression)
        implementation(rootLibs.jackson.datatype.jsr310)
        implementation(rootLibs.jackson.module.kotlin)
        implementation(rootLibs.cache4k)
    }

    tasks.named<Jar>("jar") {
        manifest {
            attributes["Main-Class"] = "io.elephantchess.webapp.MainKt"
        }

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(
            configurations["runtimeClasspath"].map {
                if (it.isDirectory) it else zipTree(it)
            },
        )
    }
}

project(":scripts") {
    dependencies {
        implementation(project(":utils"))
        implementation(project(":seven-kingdoms-core"))
        implementation(project(":seven-kingdoms-core-test-utils"))
        implementation(project(":webapp-model-common"))
        implementation(project(":webapp-dao"))
        implementation(project(":webapp-service-layer"))
        implementation(project(":webapp-config"))
        implementation(project(":webapp"))
        implementation(rootLibs.guava)
        implementation(project(":xiangqi-core"))
        implementation(project(":engine-api"))
        implementation(rootLibs.ktor.client.core)
        implementation(rootLibs.ktor.client.cio)
        implementation(rootLibs.ktor.client.logging)
        implementation(rootLibs.ktor.client.content.negotiation)
        implementation(rootLibs.opencsv)
        implementation(rootLibs.javax.mail)
        implementation(rootLibs.log4j.core)
        implementation(rootLibs.mockito.kotlin)
    }

    val sourceSets = the<SourceSetContainer>()

    tasks.register<JavaExec>("minify") {
        group = "deployment"
        description = "Minify JS and CSS assets"
        mainClass.set("io.elephantchess.scripts.minification.MinifyKt")
        classpath = sourceSets["main"].runtimeClasspath
        workingDir = rootProject.projectDir
    }

    tasks.register<JavaExec>("removeMinified") {
        group = "deployment"
        description = "Remove all minified JS and CSS files"
        mainClass.set("io.elephantchess.scripts.minification.RemoveMinifiedKt")
        classpath = sourceSets["main"].runtimeClasspath
        workingDir = rootProject.projectDir
    }

    tasks.register<JavaExec>("liquibaseGeneration") {
        group = "deployment"
        description = "Generate liquibase changelog for DB code generation"
        mainClass.set("io.elephantchess.scripts.LiquibaseGenerationKt")
        classpath = sourceSets["main"].runtimeClasspath
        workingDir = rootProject.projectDir
    }
}
