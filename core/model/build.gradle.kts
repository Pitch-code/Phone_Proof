plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin. No Android dependency of any kind — that is enforced by using the JVM
// plugin rather than the Android library plugin. Tests here run in milliseconds, which is
// what makes it cheap to test the measurement logic exhaustively.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // api, not implementation: a saved report is this model written to disk, so anything that
    // persists one needs the serializers on its own compile classpath.
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
