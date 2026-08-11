pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle download the JDK the build asks for instead of relying on whatever happens to be
    // installed. Without this, a machine whose default JDK is too new for AGP fails with a bare
    // version number and no explanation, and the workaround has to be repeated on every machine.
    // Paired with gradle/gradle-daemon-jvm.properties, which pins the daemon's own JVM.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PhoneProof"

// Pure-Kotlin logic modules. No Android dependency, so their tests run in milliseconds.
include(":core:model")
include(":core:diagnostics")
include(":checks:touch")
include(":checks:emilock")

// Android modules.
include(":core:designsystem")
include(":core:device")
include(":feature:touchgrid")
include(":feature:home")
include(":feature:diagnostics")
include(":feature:emilock")
include(":app")
