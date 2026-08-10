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
include(":checks:touch")

// Android modules.
include(":core:designsystem")
include(":feature:touchgrid")
include(":app")
