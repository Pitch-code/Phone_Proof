plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// The Android-facing readers: the thin layer that asks the platform questions and hands plain data
// to the pure-Kotlin checks. Deliberately holds no decision logic — nothing in here decides whether
// a phone is worth buying, so nothing in here needs an Android test to prove it is right.
android {
    namespace = "com.phoneproof.core.device"
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
    api(project(":checks:emilock"))
    api(project(":checks:device"))
    api(project(":checks:radios"))
    implementation(project(":core:diagnostics"))
    implementation(libs.androidx.core.ktx)
    // The storage speed test does blocking file IO, so it needs a dispatcher to get off the main thread.
    implementation(libs.kotlinx.coroutines.core)
}
