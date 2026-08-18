plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Persisted user choices. Android-side because DataStore needs a Context, but it holds no decision
// logic — it stores what the user picked and nothing else.
android {
    namespace = "com.phoneproof.core.preferences"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:designsystem"))
    implementation(project(":core:diagnostics"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    // PaidChecks is plain Kotlin, so which checks the trial excludes is testable without a device.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.withType<Test>().configureEach {
    // PassCodeTest reads licensing/code-test-vectors.txt, the one file both the Kotlin reader and the
    // JavaScript issuer are checked against. Passed in rather than reached for with a relative path, which
    // breaks the moment a test runs from a different working directory.
    systemProperty("phoneproof.repoRoot", rootProject.projectDir.absolutePath)
}
