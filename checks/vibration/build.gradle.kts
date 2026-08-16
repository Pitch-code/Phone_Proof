plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin. Whether the phone physically moved is a numbers question, and the numbers are worth
// testing against synthetic traces rather than by holding a handset and hoping.

kotlin {
    jvmToolchain(17)
}

dependencies {
    // api, not implementation: CheckResult appears in this module's public return types.
    api(project(":core:model"))

    // One checks module depending on another, which is worth a word. This check is *measured with the
    // accelerometer*, so SensorReading is the natural input type — copying it would mean two definitions of
    // the same three floats and a conversion between them at every call site.
    api(project(":checks:sensors"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
