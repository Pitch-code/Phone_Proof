package com.phoneproof.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Nothing outside a debug source set or the billing layer may grant a paid tier.
 *
 * The concern in one sentence: if a build that can hand out `PREMIUM` for free ever reaches Play, every
 * user gets every paid feature and the app earns nothing.
 *
 * That used to be prevented by a `BuildConfig.DEBUG` check, which worked and was the wrong shape — the
 * switcher, its strings and the write to storage all shipped in the release APK, one careless edit from
 * being live. The switcher now lives in `feature/settings/src/debug`, with a do-nothing twin in
 * `src/release`, so the shipped APK contains no such code at all.
 *
 * This test is what stops that arrangement being quietly undone. A source scan rather than a runtime
 * assertion, because the thing being asserted is *which variant the code is compiled into* — something no
 * test running in a single variant can observe about the other one.
 */
class PaidTierWritesTest {

    private val repoRoot: File
        get() {
            val root = System.getProperty("phoneproof.repoRoot")?.let(::File) ?: File("..")
            assertThat(root.isDirectory).isTrue()
            return root
        }

    /** Where a write of a paid tier is legitimate. */
    private fun isAllowed(file: File): Boolean {
        val path = file.invariantSeparatorsPath
        return "/src/debug/" in path ||
            // The billing layer's whole job is to grant a tier against a verified purchase.
            "/core/billing/" in path ||
            // Tests may set up any state they like.
            "/src/test/" in path
    }

    private fun sourcesWriting(pattern: Regex): List<String> =
        File(repoRoot, "feature").walkTopDown()
            .plus(File(repoRoot, "core").walkTopDown())
            .plus(File(repoRoot, "app").walkTopDown())
            .filter { it.isFile && it.extension == "kt" && "/build/" !in it.invariantSeparatorsPath }
            .filter { !isAllowed(it) }
            .filter { file ->
                file.readLines().any { line ->
                    val trimmed = line.trim()
                    !trimmed.startsWith("//") && !trimmed.startsWith("*") && pattern.containsMatchIn(line)
                }
            }
            .map { it.invariantSeparatorsPath.substringAfter(repoRoot.invariantSeparatorsPath) }
            .toList()

    @Test
    fun no_shipped_source_names_a_paid_tier_as_something_to_store() {
        // Catches the switcher being moved back into main, and catches a new screen quietly granting SHOP.
        val offenders = sourcesWriting(Regex("""setEntitlement\s*\(\s*Entitlement\.(PREMIUM|SHOP)"""))

        assertThat(offenders).isEmpty()
    }

    @Test
    fun no_shipped_source_offers_a_tier_switcher() {
        // The switcher's shape rather than its name: anything iterating every tier and writing the choice.
        val offenders = sourcesWriting(Regex("""Entitlement\.entries"""))
            .filter { "/settings/" in it }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun the_release_variant_has_a_do_nothing_tier_override() {
        // The other half of the arrangement. Without this file the release variant would not compile, so
        // this is really asserting that the do-nothing twin has not been deleted as apparently redundant.
        val release = File(
            repoRoot,
            "feature/settings/src/release/kotlin/com/phoneproof/feature/settings/TierOverride.kt",
        )

        assertThat(release.isFile).isTrue()

        val body = release.readText()
        assertThat(body).contains("internal fun TierOverride")
        // It must not actually do anything with the callback it is handed.
        assertThat(body).doesNotContain("onSelect(")
        assertThat(body).doesNotContain("Section(")
    }

    @Test
    fun the_debug_variant_still_has_a_working_one() {
        // The affordance has to keep existing, or the paid screens become untestable: Play Billing cannot
        // complete a purchase in a sideloaded build, so there is no other way to reach them.
        val debug = File(
            repoRoot,
            "feature/settings/src/debug/kotlin/com/phoneproof/feature/settings/TierOverride.kt",
        )

        assertThat(debug.isFile).isTrue()
        assertThat(debug.readText()).contains("onSelect(")
    }

    @Test
    fun both_copies_declare_the_same_function() {
        // If the signatures drift, one variant stops compiling — and the one that stops is whichever
        // nobody built recently, which for most of this project's life has been release.
        val root = "feature/settings/src"
        val debug = File(repoRoot, "$root/debug/kotlin/com/phoneproof/feature/settings/TierOverride.kt")
        val release = File(repoRoot, "$root/release/kotlin/com/phoneproof/feature/settings/TierOverride.kt")

        val signature = Regex("""internal fun TierOverride\(\s*current: Entitlement,\s*onSelect: \(Entitlement\) -> Unit,\s*\)""")

        assertThat(signature.containsMatchIn(debug.readText())).isTrue()
        assertThat(signature.containsMatchIn(release.readText())).isTrue()
    }
}
