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

    signingConfigs {
        /**
         * A debug key that is committed to the repository, on purpose.
         *
         * Without this, every machine and every CI run generates its own throwaway `~/.android/debug.keystore`.
         * Two builds of the same commit then carry different signatures, and Android refuses to install one
         * over the other: "App not installed". Anyone testing successive builds has to uninstall first, which
         * also throws away their saved reports — so the app's own report history could never be tested across
         * more than one build.
         *
         * Committing it is safe and is the standard practice for a *debug* key:
         *
         *  - it cannot publish anything. Play rejects an APK signed with a debug certificate outright.
         *  - the password is the universally known "android", the same as the one Android generates. There is
         *    no secret here to leak.
         *  - `applicationIdSuffix = ".debug"` keeps these builds in their own package, so a debug build can
         *    never overwrite or impersonate a real installation.
         *
         * **The release key is a completely different matter and must never be committed.** `.gitignore` still
         * excludes every `*.keystore` and `*.jks`; this one file is a single deliberate exception, and
         * `keystore.properties` stays ignored so release credentials are supplied by the signing environment.
         */
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(project(":feature:storagespeed"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:screentest"))
    implementation(project(":feature:guide"))
    implementation(project(":feature:claims"))
    implementation(project(":feature:imei"))
    implementation(project(":feature:audiotest"))
    implementation(project(":feature:buttons"))
    implementation(project(":feature:cameratest"))
    implementation(project(":feature:charging"))
    implementation(project(":feature:radios"))
    implementation(project(":feature:sensortest"))
    implementation(project(":feature:vibration"))
    // Brings :core:run transitively, since RunState and RunSession are in feature:run's public API.
    implementation(project(":feature:run"))
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

    // The app module's first unit tests. Plain JVM, no Robolectric: the only thing asserted here is
    // that the guided run's step ids and the navigation graph's route names are the same strings.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
