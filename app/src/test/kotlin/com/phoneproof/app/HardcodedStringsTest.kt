package com.phoneproof.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The ratchet that stops the localisation work from quietly reversing.
 *
 * Externalising eight hundred strings is not one change, so the only way it finishes is if it cannot go
 * backwards. This walks the source of every UI module and fails when it finds user-facing text written
 * directly in Kotlin. Modules already converted are enforced; the rest sit in [NOT_YET_CONVERTED], and
 * that list is only ever allowed to get shorter.
 *
 * It also doubles as the to-do list, which is why it is a test and not a document: a document saying
 * which modules still need doing would be out of date within a fortnight.
 *
 * Deliberately a source scan rather than a lint rule. Android's own `HardcodedText` check only looks at
 * XML layouts, and this app has none — every screen is Compose, where the equivalent problem is a string
 * literal passed to a composable parameter.
 */
class HardcodedStringsTest {

    /**
     * Modules whose UI text is still in Kotlin.
     *
     * Remove a name from here in the same commit that converts it. Adding one back is not a fix.
     */
    private val notYetConverted = setOf(
        "audiotest",
        "buttons",
        "cameratest",
        "claims",
        "diagnostics",
        "emilock",
        "guide",
        "home",
        "imei",
        "radios",
        "run",
        "scan",
        "screentest",
        "sensortest",
        "settings",
        "storagespeed",
        "touchgrid",
        "vibration",
    )

    /**
     * Parameters whose value is read aloud or displayed, so a literal there is untranslated text.
     *
     * Not every literal in a composable is a problem — test tags, semantic keys, animation labels and
     * format patterns are all fine — so this names the ones that are rather than trying to exclude the
     * ones that are not.
     */
    private val userFacingParameters = listOf(
        "text", "headline", "detail", "instruction", "subtitle", "body",
        "retestLabel", "backLabel", "action", "label", "title", "note", "summary",
    )

    private val repoRoot: File
        get() {
            val fromProperty = System.getProperty("phoneproof.repoRoot")
            val root = fromProperty?.let(::File) ?: File("..")
            assertThat(root.isDirectory).isTrue()
            return root
        }

    private fun uiSources(module: File): List<File> =
        File(module, "src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    /**
     * `text = "Something"`, and not `text = stringResource(...)`.
     *
     * Built from [userFacingParameters] so the two cannot drift apart. Two characters is the shortest
     * thing worth translating; it also lets `text = ""` past, which is a spacer rather than a sentence.
     */
    private val literal = Regex(
        """\b(${userFacingParameters.joinToString("|")})\s*=\s*"[^"]{2,}"""",
    )

    private fun offenders(module: File): List<String> = uiSources(module).flatMap { file ->
        file.readLines().mapIndexedNotNull { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("//") || trimmed.startsWith("*") -> null
                literal.containsMatchIn(line) -> "${file.name}:${index + 1}  $trimmed"
                else -> null
            }
        }
    }

    @Test
    fun converted_modules_keep_their_text_in_resources() {
        val featureDir = File(repoRoot, "feature")
        assertThat(featureDir.isDirectory).isTrue()

        val converted = featureDir.listFiles()!!
            .filter { it.isDirectory && it.name !in notYetConverted }

        // Guards the ratchet itself: if this ever finds nothing to enforce, the test has stopped working
        // rather than the codebase having become clean.
        assertThat(converted).isNotEmpty()

        val failures = converted.flatMap { module ->
            offenders(module).map { "${module.name}/$it" }
        }

        assertThat(failures).isEmpty()
    }

    @Test
    fun the_allowlist_names_only_modules_that_exist() {
        // A stale name would silently exempt nothing, and would make the remaining work look larger than
        // it is — or hide a module that was never converted at all.
        val actual = File(repoRoot, "feature").listFiles()!!.filter { it.isDirectory }.map { it.name }

        assertThat(actual).containsAtLeastElementsIn(notYetConverted)
    }

    @Test
    fun the_allowlist_only_ever_gets_shorter() {
        // The number is written down so shortening it is a deliberate act with a diff, and lengthening it
        // is impossible to do by accident. Update it in the same commit that converts a module.
        assertThat(notYetConverted).hasSize(18)
    }

    @Test
    fun no_module_counts_a_plural_with_an_if() {
        // English has two cases and puts the boundary at one. Hindi, Tamil, Telugu and Urdu do not all
        // agree with that, so `if (n == 1) "" else "s"` is not a style choice — it is a sentence that
        // cannot be translated correctly. Enforced only for converted modules, like the rule above.
        val featureDir = File(repoRoot, "feature")
        val converted = featureDir.listFiles()!!
            .filter { it.isDirectory && it.name !in notYetConverted }

        val suspicious = converted.flatMap { module ->
            uiSources(module).flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (line.contains("\"\" else \"s\"") || line.contains("nounFor(")) {
                        "${module.name}/${file.name}:${index + 1}"
                    } else {
                        null
                    }
                }
            }
        }

        assertThat(suspicious).isEmpty()
    }
}
