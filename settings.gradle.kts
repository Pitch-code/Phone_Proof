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
include(":checks:buttons")
include(":checks:touch")
include(":checks:vibration")
include(":checks:emilock")
include(":checks:device")
// Pure Kotlin even by the standards of this list: Android will not hand an app the IMEI at all, so
// there is no platform reader to pair with it and nothing to mock.
include(":checks:imei")
// The audio analysis. Worth keeping pure more than most: there is no microphone in the build
// environment, so the only way to know a tone detector detects tones is to feed it a synthesised tone.
include(":checks:media")
include(":checks:sensors")

// Android modules.
include(":core:designsystem")
// Permission plumbing, kept out of core:designsystem so a module that wants a colour does not inherit
// activity-result APIs.
include(":core:permissions")
// Audio capture: the AudioRecord/AudioTrack layer feeding checks:media.
include(":core:media")
include(":core:device")
include(":core:preferences")
include(":core:sensors")
include(":core:reports")
include(":core:run")
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
include(":feature:imei")
include(":feature:audiotest")
include(":feature:buttons")
include(":feature:cameratest")
include(":feature:sensortest")
include(":feature:vibration")
include(":feature:run")
include(":app")
