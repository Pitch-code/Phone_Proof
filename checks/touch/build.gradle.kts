plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Deliberately pure Kotlin. The touch-coverage algorithm decides whether a buyer walks away
// from an 18,000 rupee phone, so it gets tested exhaustively — and that is only cheap if the
// tests never touch the Android framework.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // api, not implementation: CheckResult appears in this module's public return types.
    api(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
