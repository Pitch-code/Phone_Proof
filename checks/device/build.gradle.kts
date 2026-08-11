plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin. Six device checks live here, each one a function from measured facts to a verdict,
// so all of them are tested in milliseconds without an emulator.
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
