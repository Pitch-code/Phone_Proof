plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin. No Android dependency of any kind — that is enforced by using the JVM
// plugin rather than the Android library plugin. Tests here run in milliseconds, which is
// what makes it cheap to test the measurement logic exhaustively.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
