plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin. The interesting part of this check is which silences mean something and which mean the
// app never received a key press at all, and that reasoning is worth testing exhaustively without an
// emulator in the way.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // api, not implementation: CheckResult appears in this module's public return types.
    api(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
