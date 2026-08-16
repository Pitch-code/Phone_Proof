plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin. The whole difficulty here is which silences are evidence and which are just a shop with no
// Wi-Fi to join, so the rules get tested exhaustively away from any actual radio.

kotlin {
    jvmToolchain(17)
}

dependencies {
    // api, not implementation: CheckResult appears in this module's public return types.
    api(project(":core:model"))


    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
