pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "elephantchess.io"

include("utils")
include("engine-api")
include("csv-dump-parser")
include("xiangqi-core")
include("xiangqi-core-test-utils")
include("seven-kingdoms-core")
include("seven-kingdoms-core-test-utils")
include("webapp-config")
include("webapp-dao")
include("webapp-dao-migration")
include("webapp-html-renderer")
include("webapp-model-common")
include("webapp-service-layer")
include("webapp")
include("scripts")
