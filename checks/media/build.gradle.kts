plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure Kotlin, and this module is the reason the audio feature is worth attempting at all.
//
// Everything that decides whether a microphone or a speaker is faulty is arithmetic over a buffer of
// samples: root-mean-square levels, a noise floor, and a Goertzel filter looking for one frequency. None
// of that needs Android, so all of it can be tested against synthesised waveforms where the right answer
// is known exactly — which matters more here than anywhere else in the app, because there is no device
// in this environment to check the real thing against.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // api, not implementation: CheckResult appears in this module's public return types.
    api(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
