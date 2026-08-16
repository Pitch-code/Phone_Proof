plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin, and this one especially. Telling a seller their proximity sensor is broken when it is
// not is the exact failure this project was started in reaction to, so the rules that produce that
// accusation are tested against synthetic traces at speed rather than by waving a phone around.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // api, not implementation: CheckResult appears in this module's public return types.
    api(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
