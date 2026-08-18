// Imported rather than written as java.util.Properties, which does not compile in a Kotlin DSL script:
// the Java plugin contributes an extension accessor called `java`, so `java.util` resolves `java` to that
// extension and then fails to find `util` on it. A confusing error for an ordinary-looking line.
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The version, read from `version.properties` at the repo root rather than written here.
 *
 * It was `versionCode = 1` and `versionName = "0.1.0"`, hardcoded, through four tagged releases. Two
 * consequences, both live:
 *
 *  - **Play would have rejected the second upload.** `versionCode` must strictly increase, and this one
 *    never moved. That is a publishing blocker that only shows up on the second attempt, which is the
 *    worst time to discover it.
 *  - **Every report the app has ever written names the wrong build.** `BuildConfig.VERSION_NAME` goes into
 *    the diagnostics log and into every saved report, so a bug report from the v0.4.0 APK says 0.1.0. A
 *    version number that lies is worse than no version number, because it is believed.
 *
 * Read through `providers.fileContents` rather than `File.readText` so the configuration cache knows this
 * build depends on the file and re-runs configuration when it changes. Reading it directly would be
 * invisible to the cache, and the first stale build would produce an APK with the previous version in it.
 *
 * Located via `layout.settingsDirectory` rather than `rootProject.file(...)`: both find the same file, but
 * this one does not reach into another project's model during configuration, which keeps the build sound
 * under isolated projects.
 */
val versionNameFromFile: String =
    providers.fileContents(layout.settingsDirectory.file("version.properties"))
        .asText
        .orNull
        ?.let { text ->
            Properties()
                .apply { load(StringReader(text)) }
                .getProperty("versionName")
                ?.trim()
        }
        ?.takeIf { it.isNotEmpty() }
        ?: throw GradleException(
            "version.properties is missing from the repo root, or does not define versionName. " +
                "It should contain a single line like: versionName=0.5.0",
        )

/**
 * `MAJOR*10000 + MINOR*100 + PATCH`, derived rather than stored.
 *
 * Two numbers that must agree, both maintained by hand, eventually disagree — and the failure is silent
 * until Play rejects the upload. Deriving one from the other removes the possibility instead of relying on
 * whoever is releasing to remember.
 *
 * The ceiling on MINOR and PATCH is what makes it monotonic: without it, 1.9.0 (10900) would outrank
 * 1.10.0 (11000)'s intended ordering only by luck, and 0.0.100 would collide with 0.1.0.
 */
val versionCodeFromFile: Int = run {
    val parts = versionNameFromFile.split(".")
    if (parts.size != 3) {
        throw GradleException(
            "versionName in version.properties must be MAJOR.MINOR.PATCH with no suffix; " +
                "found '$versionNameFromFile'.",
        )
    }
    val (major, minor, patch) = parts.map { part ->
        part.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw GradleException(
                "versionName in version.properties must be three non-negative numbers; " +
                    "found '$versionNameFromFile'.",
            )
    }
    if (minor > 99 || patch > 99) {
        throw GradleException(
            "MINOR and PATCH must stay under 100 so the derived versionCode keeps increasing; " +
                "found '$versionNameFromFile'. Raise MAJOR or MINOR instead.",
        )
    }
    major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.phoneproof.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.phoneproof.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = versionCodeFromFile
        versionName = versionNameFromFile
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

tasks.withType<Test>().configureEach {
    // HardcodedStringsTest walks the source of every feature module, so it needs to know where the repo
    // starts. Passed in rather than guessed from a relative path, which breaks the moment a test is run
    // from a different working directory.
    systemProperty("phoneproof.repoRoot", rootProject.projectDir.absolutePath)
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
    implementation(project(":core:billing"))
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
