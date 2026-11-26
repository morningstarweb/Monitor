pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Monitor"

include(":app")
include(":core:common")
include(":core:model")
include(":core:network")
include(":core:designsystem")
include(":feature:dashboard")
include(":feature:gitworkspace")
include(":feature:onboarding")

