plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.phoneproof.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.phoneproof.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signing config is intentionally absent. Keystores never enter this repo.
        }
    }

    buildFeatures {
        compose = true
        // Needed so the diagnostics report can state which build produced it. A log without a
        // version is guesswork.
        buildConfig = true
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
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:diagnostics"))
    implementation(project(":feature:home"))
    implementation(project(":feature:touchgrid"))
    implementation(project(":feature:emilock"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:screentest"))
    implementation(project(":feature:guide"))
    implementation(project(":feature:claims"))
    implementation(project(":feature:imei"))
    implementation(project(":feature:audiotest"))
    implementation(project(":core:preferences"))
    implementation(project(":feature:diagnostics"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
