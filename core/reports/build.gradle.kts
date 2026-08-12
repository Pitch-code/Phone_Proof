plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin, deliberately. Saving a report is java.io.File plus JSON, neither of which needs
// Android, so the JVM plugin is used to make an Android import a compile error rather than a
// choice. The payoff is that retention, pruning and corrupt-file recovery are all testable in
// milliseconds against a temp directory — this sandbox has no emulator, so anything requiring a
// Context would effectively ship untested.
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
