plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin, like the `checks:*` modules and for the same reason. The order of the steps and the
// rule that decides "walk away" rather than "negotiate" are the two things in this app most likely
// to be argued about, so they are tested exhaustively — and that is only cheap when the tests never
// touch the Android framework.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // api, not implementation: CheckResult and CheckOutcome appear in this module's public types.
    api(project(":core:model"))

    // StateFlow only. No Android, no lifecycle: RunSession is a plain observable holder.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
