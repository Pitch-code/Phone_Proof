plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.phoneproof.core.designsystem"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Required by Robolectric so Compose can be inflated and rendered on the JVM.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// Screenshots land in a single repo-level folder so they are reviewable on GitHub and in the
// file explorer. Computed at configuration time to stay configuration-cache safe.
// Screenshot tests need androidx.compose.ui:ui-test-manifest, which is deliberately a
// debug-only dependency — shipping a test manifest into a release build would be wrong. The
// release variant therefore has no activity for Robolectric to launch, so its unit tests are
// not run. Screenshot verification is a debug-variant concern by design.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { enabled = false }

val screenshotDir: String =
    rootProject.layout.projectDirectory.dir("screenshots").asFile.absolutePath

tasks.withType<Test>().configureEach {
    systemProperty("phoneproof.screenshotDir", screenshotDir)
    systemProperty("robolectric.graphicsMode", "NATIVE")
}

dependencies {
    // api: CheckOutcome appears in the public signature of shared components like OutcomeBadge.
    api(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    // ResultActions reaches the activity's own back dispatcher, so the Back button on a result screen
    // and the system back gesture are literally the same call and cannot drift apart.
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
