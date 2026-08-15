plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin, and unusually satisfying to keep that way: an IMEI check is *entirely* arithmetic.
// Android will not give an app the IMEI at all — it has been privileged since Android 10 — so there
// is nothing to read from the platform and nothing to mock. The buyer types fifteen digits and this
// module decides what can honestly be said about them.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // api, not implementation: CheckResult appears in this module's public return types.
    api(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
