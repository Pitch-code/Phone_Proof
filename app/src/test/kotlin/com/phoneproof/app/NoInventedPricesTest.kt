package com.phoneproof.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * That no verdict tells a buyer to knock a specific amount off the price.
 *
 * The app used to. Five separate checks said things like *"worth 1,500 off"* and *"ask for 500 off"*, and
 * every one of them was a guess dressed as a measurement:
 *
 *  - **Repair prices vary enormously** by city, by model, by whether the part is original, and by year. A
 *    figure that is roughly right in one market is wildly wrong in another, and this app is aimed squarely at
 *    a country with a very wide spread.
 *  - **It is the one number a buyer would repeat out loud.** Everything else the app says is an observation
 *    it can defend; a price is an assertion about somebody else's market that it cannot.
 *  - **It reads as authority.** A buyer who says "the app says 1,500" and is laughed at by a seller has been
 *    embarrassed by us, and will trust the rest of the verdict less — including the parts that are sound.
 *
 * The advice that replaced it is better as well as safer: get the repair quoted locally and take *that* off.
 * A buyer holding a real quote negotiates from evidence rather than from a number an app invented.
 *
 * They also had no currency symbol, which became visible on a public Play store screenshot — how this was
 * noticed at all.
 */
class NoInventedPricesTest {

    private val repoRoot: File
        get() {
            val fromProperty = System.getProperty("phoneproof.repoRoot")
            val root = fromProperty?.let(::File) ?: File("..")
            assertThat(root.isDirectory).isTrue()
            return root
        }

    /**
     * `1,000 off`, `500 off`, `2000 off`.
     *
     * Deliberately narrow: it matches a number immediately followed by "off", which is the negotiation
     * phrasing, and not the many legitimate numbers in this code — sample counts, milliamps, seconds,
     * megabytes per second. A broader pattern would have to be muted so often that it would stop being read.
     */
    private val amountOff = Regex("""\b\d[\d,]*\s+off\b""")

    private fun verdictSources(): List<File> {
        val checks = File(repoRoot, "checks")
        assertThat(checks.isDirectory).isTrue()

        return checks.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("/src/test/") }
            .toList()
    }

    @Test
    fun the_verdicts_are_actually_being_read() {
        // Guards the test: a walk that found nothing would make the assertions below pass for the wrong
        // reason, which is the failure that hides every other one.
        val sources = verdictSources()

        assertThat(sources).isNotEmpty()
        assertThat(sources.size).isAtLeast(8)
    }

    @Test
    fun no_verdict_names_an_amount_to_knock_off_the_price() {
        val offenders = verdictSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                // Comments are skipped, exactly as HardcodedStringsTest skips them. One doc comment in
                // VibrationCheck quotes the old wording while explaining why it was removed, and a test that
                // forbade describing a mistake would push people towards deleting the explanation instead.
                when {
                    trimmed.startsWith("//") || trimmed.startsWith("*") -> null
                    amountOff.containsMatchIn(line) -> "${file.name}:${index + 1}  $trimmed"
                    else -> null
                }
            }
        }

        // Named rather than counted, because the useful information when this fails is which check started
        // quoting a price and what it said.
        assertThat(offenders).isEmpty()
    }

    @Test
    fun no_verdict_prints_a_currency_symbol() {
        // Prices belong to Play, which localises them, and repair costs belong to whoever is quoting. Neither
        // has any business in a measurement's verdict.
        val offenders = verdictSources()
            .filter { file ->
                file.readLines().any { line ->
                    val trimmed = line.trim()
                    !trimmed.startsWith("//") && !trimmed.startsWith("*") && line.contains("₹")
                }
            }
            .map { it.name }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun the_advice_that_replaced_it_still_tells_a_buyer_what_to_do() {
        // Removing a bad claim must not leave a verdict saying nothing actionable. The point was never to
        // stop advising, only to stop inventing — so at least one check should now point at a real quote.
        val advice = verdictSources().joinToString(" ") { it.readText() }

        assertThat(advice).contains("quoted")
    }
}
