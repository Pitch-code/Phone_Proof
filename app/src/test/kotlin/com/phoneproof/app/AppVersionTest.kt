package com.phoneproof.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Properties
import org.junit.Test

/**
 * That the APK carries the version the repo says it does.
 *
 * The build derives both numbers from `version.properties`, which is the fix. This is the part that checks
 * the fix actually reached the APK: `BuildConfig` is generated, so a mistake in the wiring would produce a
 * build that compiles perfectly and reports the wrong version — the exact failure that went unnoticed
 * through four releases, all of which said 0.1.0 inside whatever the download was called.
 *
 * Deliberately reading the file rather than trusting a constant. A test that restated the expected numbers
 * would pass while agreeing with itself and nothing else.
 */
class AppVersionTest {

    private val declaredVersionName: String
        get() {
            // Passed in by the build; guessing a relative path breaks when tests run from elsewhere.
            val root = System.getProperty("phoneproof.repoRoot")
            assertThat(root).isNotNull()

            val file = File(root, "version.properties")
            assertThat(file.isFile).isTrue()

            val declared = file.inputStream().use { stream ->
                Properties().apply { load(stream) }.getProperty("versionName")
            }
            assertThat(declared).isNotNull()
            return declared!!.trim()
        }

    @Test
    fun the_apk_reports_the_version_the_repo_declares() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo(declaredVersionName)
    }

    @Test
    fun the_version_code_is_derived_from_the_version_name() {
        // Recomputed here from BuildConfig's own name, independently of the build script. If the two ever
        // disagree it means one was edited without the other, which is precisely what deriving it was meant
        // to make impossible.
        val (major, minor, patch) = BuildConfig.VERSION_NAME.split(".").map { it.toInt() }

        assertThat(BuildConfig.VERSION_CODE).isEqualTo(major * 10_000 + minor * 100 + patch)
    }

    @Test
    fun the_version_name_is_three_plain_numbers() {
        // No `-beta`, no `-radios`. A suffix cannot be turned into a versionCode, and one tag in this repo's
        // history (v0.2.1-radios) shows the temptation is real.
        assertThat(BuildConfig.VERSION_NAME).matches("""\d+\.\d+\.\d+""")
    }

    @Test
    fun the_version_code_has_moved_off_the_hardcoded_one() {
        // Play refuses an upload whose versionCode does not exceed the last one accepted. Four releases went
        // out carrying 1, so anything still reporting 1 means the wiring silently fell back to the old
        // constant — a failure that would otherwise surface for the first time at the Console.
        assertThat(BuildConfig.VERSION_CODE).isGreaterThan(1)
    }

    @Test
    fun minor_and_patch_stay_under_the_ceiling_the_derivation_needs() {
        // 0.0.100 and 0.1.0 would derive the same code, and the second upload would be rejected as a
        // duplicate. The build fails on this too; asserted here so the reason is written down somewhere a
        // person reads.
        val (_, minor, patch) = BuildConfig.VERSION_NAME.split(".").map { it.toInt() }

        assertThat(minor).isLessThan(100)
        assertThat(patch).isLessThan(100)
    }
}
