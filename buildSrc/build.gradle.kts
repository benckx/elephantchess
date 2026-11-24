plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jooq.codegen)
    implementation(libs.h2)
    implementation(libs.liquibase.core)
}

gradlePlugin {
    plugins {
        register("daoCodeGen") {
            id = "io.elephantchess.dao-code-gen"
            implementationClass = "DaoCodeGenPlugin"
        }
    }
}
