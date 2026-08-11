plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin. The recorder has to work when the app is already misbehaving, so it depends on
// nothing that could itself be the thing that broke.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
