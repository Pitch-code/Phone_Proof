plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.phoneproof.feature.scan"
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
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

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
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:device"))
    implementation(project(":core:reports"))
    implementation(project(":core:preferences"))
    implementation(project(":checks:device"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
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
