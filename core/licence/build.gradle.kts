plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Talks to the licence server, and does nothing else.
//
// Separate from :core:preferences on purpose. That module decides *what a tier allows*; this one makes two
// HTTP requests. Keeping them apart means the rules about who may run which check stay testable without a
// network, which is most of why they are trustworthy.
android {
    namespace = "com.phoneproof.core.licence"
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
    // InspectionPass and PassCode: the shapes this module returns, and the offline check it repeats.
    api(project(":core:preferences"))
    implementation(project(":core:diagnostics"))
    implementation(libs.kotlinx.coroutines.core)

    // No HTTP library. Two requests do not justify a dependency, and HttpURLConnection is in the platform.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
