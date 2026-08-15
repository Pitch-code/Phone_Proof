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
include(":checks:device")
// Pure Kotlin even by the standards of this list: Android will not hand an app the IMEI at all, so
// there is no platform reader to pair with it and nothing to mock.
include(":checks:imei")

// Android modules.
include(":core:designsystem")
include(":core:device")
include(":core:preferences")
include(":core:reports")
include(":feature:touchgrid")
include(":feature:home")
include(":feature:scan")
include(":feature:settings")
include(":feature:reports")
include(":feature:screentest")
include(":feature:guide")
include(":feature:claims")
include(":feature:diagnostics")
include(":feature:emilock")
include(":app")
