import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.h2.Driver
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import java.sql.Connection

val kotlinVersion: String by project
val coroutineVersion: String by project
val kTorVersion: String by project
val koinCoreVersion: String by project
val postgresVersion: String by project
val liquibaseVersion: String by project
val jooqVersion: String by project
val h2Version: String by project
val jacksonVersion: String by project
val jsoupVersion: String by project
val mockitoVersion: String by project
val cache4kVersion: String by project
val commonLang3Version: String by project
val javaxMailVersion: String by project
val openCsvVersion: String by project
val testContainerVersion: String by project
val guavaVersion: String by project
val logbackVersion: String by project

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jooq:jooq-codegen:${project.property("jooqVersion")}")
        classpath("com.h2database:h2:${project.property("h2Version")}")
        classpath("org.liquibase:liquibase-core:${project.property("liquibaseVersion")}")
    }
}

plugins {
    id("com.github.ben-manes.versions") version "0.54.0"
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
    id("com.adarshr.test-logger") version "4.0.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
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
        add("implementation", "io.github.oshai:kotlin-logging-jvm:8.0.02")
        add("implementation", "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
        add("implementation", "ch.qos.logback:logback-classic:$logbackVersion")
        add("testImplementation", "org.jetbrains.kotlin:kotlin-test")
        add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutineVersion")
        add("testImplementation", "org.mockito.kotlin:mockito-kotlin:$mockitoVersion")
        add("testImplementation", "org.junit.jupiter:junit-jupiter-params:6.0.3")
    }

    val nettyVersion = "4.2.12.Final"
    configurations.configureEach {
        resolutionStrategy {
            force("org.apache.commons:commons-lang3:$commonLang3Version")
            force("org.checkerframework:checker-qual:4.1.0")
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
        add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
        add("testImplementation", project(":xiangqi-core"))
    }
}

project(":xiangqi-core") {
    dependencies {
        add("testImplementation", project(":xiangqi-core-test-utils"))
    }
}

project(":seven-kingdoms-core") {
    dependencies {
        add("testImplementation", project(":seven-kingdoms-core-test-utils"))
    }
}

project(":seven-kingdoms-core-test-utils") {
    dependencies {
        add("implementation", project(":seven-kingdoms-core"))
        add("api", "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        add("api", "com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    }
}

project(":webapp-dao-migration") {
    dependencies {
        add("implementation", "org.liquibase:liquibase-core:$liquibaseVersion")
        add("implementation", "org.postgresql:postgresql:$postgresVersion")
    }
}

project(":webapp-dao") {
    the<SourceSetContainer>()["main"].java.srcDir(layout.buildDirectory.dir("jooq"))

    dependencies {
        add("implementation", project(":utils"))
        add("implementation", project(":webapp-dao-migration"))
        add("implementation", project(":seven-kingdoms-core"))
        add("implementation", project(":webapp-config"))
        add("implementation", project(":webapp-model-common"))
        add("implementation", project(":xiangqi-core"))
        add("implementation", project(":engine-api"))
        add("implementation", "org.jooq:jooq:$jooqVersion")
        add("implementation", "org.jooq:jooq-kotlin:$jooqVersion")
        add("implementation", "org.jooq:jooq-kotlin-coroutines:$jooqVersion")
        add("implementation", "org.liquibase:liquibase-core:$liquibaseVersion")
        add("implementation", "org.postgresql:r2dbc-postgresql:1.1.1.RELEASE")
        add("implementation", "io.r2dbc:r2dbc-pool:1.0.2.RELEASE")
    }
}

project(":webapp-html-renderer") {
    dependencies {
        add("implementation", project(":utils"))
        add("implementation", "org.jsoup:jsoup:$jsoupVersion")
    }
}

project(":webapp-service-layer") {
    dependencies {
        add("api", project(":utils"))
        add("api", project(":webapp-config"))
        add("api", project(":webapp-model-common"))
        add("api", project(":webapp-html-renderer"))
        add("implementation", project(":seven-kingdoms-core"))
        add("implementation", project(":webapp-dao"))
        add("api", "io.insert-koin:koin-core:$koinCoreVersion")
        add("api", project(":xiangqi-core"))
        add("api", project(":engine-api"))
        add("api", "org.apache.commons:commons-lang3:$commonLang3Version")
        add("implementation", "org.jsoup:jsoup:$jsoupVersion")
        add("implementation", "commons-validator:commons-validator:1.10.1")
        add("implementation", "com.auth0:java-jwt:4.5.2")
        add("implementation", "com.sun.mail:javax.mail:$javaxMailVersion")
        add("implementation", "io.github.reactivecircus.cache4k:cache4k:$cache4kVersion")
        add("implementation", "io.fabric8:kubernetes-client:7.6.1")
        add("implementation", "org.jooq:jooq:$jooqVersion")
        add("implementation", "io.ktor:ktor-client-core:$kTorVersion")
        add("implementation", "io.ktor:ktor-client-cio:$kTorVersion")
        add("implementation", "io.ktor:ktor-client-content-negotiation:$kTorVersion")
        add("implementation", "io.ktor:ktor-client-logging:$kTorVersion")
        add("implementation", "io.ktor:ktor-serialization-jackson:$kTorVersion")
        add("implementation", "io.ktor:ktor-serialization-kotlinx-json:$kTorVersion")
        add("testImplementation", project(":xiangqi-core-test-utils"))
        add("testImplementation", project(":seven-kingdoms-core-test-utils"))
        add("testImplementation", "org.apache.commons:commons-rng-simple:1.7")
        add("testImplementation", "org.apache.commons:commons-text:1.15.0")
        add("testImplementation", "com.h2database:h2:$h2Version")
        add("testImplementation", "org.liquibase:liquibase-core:$liquibaseVersion")
        add("testImplementation", "org.testcontainers:postgresql:$testContainerVersion")
    }
}

project(":webapp-config") {
    dependencies {
        add("implementation", "commons-cli:commons-cli:1.11.0")
    }
}

project(":webapp") {
    dependencies {
        add("implementation", project(":webapp-service-layer"))
        add("implementation", "io.ktor:ktor-server-core:$kTorVersion")
        add("implementation", "io.ktor:ktor-server-netty:$kTorVersion")
        add("implementation", "io.ktor:ktor-server-status-pages:$kTorVersion")
        add("implementation", "io.ktor:ktor-server-default-headers:$kTorVersion")
        add("implementation", "io.ktor:ktor-server-content-negotiation:$kTorVersion")
        add("implementation", "io.ktor:ktor-server-caching-headers:$kTorVersion")
        add("implementation", "io.ktor:ktor-serialization-jackson:$kTorVersion")
        add("implementation", "io.ktor:ktor-serialization-kotlinx-json:$kTorVersion")
        add("implementation", "io.ktor:ktor-server-websockets:$kTorVersion")
        add("implementation", "io.ktor:ktor-server-default-headers:$kTorVersion")
        add("implementation", "io.ktor:ktor-server-compression:$kTorVersion")
        add("implementation", "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        add("implementation", "io.github.reactivecircus.cache4k:cache4k:$cache4kVersion")
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
        add("implementation", project(":utils"))
        add("implementation", project(":seven-kingdoms-core"))
        add("implementation", project(":seven-kingdoms-core-test-utils"))
        add("implementation", project(":webapp-model-common"))
        add("implementation", project(":webapp-dao"))
        add("implementation", project(":webapp-service-layer"))
        add("implementation", project(":webapp-config"))
        add("implementation", project(":webapp"))
        add("implementation", "com.google.guava:guava:$guavaVersion")
        add("implementation", project(":xiangqi-core"))
        add("implementation", project(":engine-api"))
        add("implementation", "io.ktor:ktor-client-core:$kTorVersion")
        add("implementation", "io.ktor:ktor-client-cio:$kTorVersion")
        add("implementation", "io.ktor:ktor-client-logging:$kTorVersion")
        add("implementation", "io.ktor:ktor-client-content-negotiation:$kTorVersion")
        add("implementation", "com.opencsv:opencsv:$openCsvVersion")
        add("implementation", "com.sun.mail:javax.mail:$javaxMailVersion")
        add("implementation", "org.apache.logging.log4j:log4j-core:2.26.0")
        add("implementation", "org.mockito.kotlin:mockito-kotlin:$mockitoVersion")
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
