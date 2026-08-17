plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Play Billing.
//
// An Android library because BillingClient needs a Context, but the decision logic in here — what a set
// of purchases entitles someone to, and which of them still need acknowledging — is deliberately plain
// Kotlin with no Android imports. That half is unit-tested without Robolectric, which matters more than
// usual: the Play interaction itself cannot be tested anywhere except on a device signed into a Play
// account, so everything that *can* be tested off-device should be.
android {
    namespace = "com.phoneproof.core.billing"
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
    // api: Entitlement appears in the public signature of the reconciler.
    api(project(":core:preferences"))
    implementation(project(":core:diagnostics"))
    implementation(libs.billing.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
