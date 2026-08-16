plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// The Android-facing audio capture, in the same spirit as core:device: it asks the platform for samples
// and hands a plain buffer to the pure-Kotlin analysis in checks:media. No decision logic lives here, so
// nothing in here needs a device to prove it is right — which matters, because a device is the one thing
// this project does not have.
android {
    namespace = "com.phoneproof.core.media"
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
    // api: AudioWindow appears in this module's public return types.
    api(project(":checks:media"))
    implementation(project(":core:diagnostics"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
