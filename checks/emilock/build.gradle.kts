plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin. This check decides whether a phone can be bricked remotely after money changes
// hands, so its logic is tested exhaustively — cheap only because no Android framework is involved.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // api: CheckResult appears in this module's public return types.
    api(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
