package com.phoneproof.checks.imei

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * Turns fifteen typed digits into a report row.
 *
 * The check is titled "IMEI checksum" rather than "IMEI", and that is not fussiness. A row called
 * IMEI showing a green PASS answers a question the app never asked — a buyer reads it as "this phone
 * is not stolen", which depends on a national blocklist this app has no access to and no business
 * guessing at. The title names what was actually measured, which is arithmetic.
 *
 * For the same reason the blocklist is never reported as an outcome. It is carried in the [action] of
 * every result, pointing at the official CEIR portal, so the one thing the app cannot answer is always
 * handed to the service that can.
 */
object ImeiCheck {

    /**
     * `security.`, because the category chip on the report card is derived from this namespace.
     *
     * It was `identity.` first, which is arguably the more precise word and was wrong for a practical
     * reason: `CheckCategory` maps four namespaces and falls back to a generic "CHECK" chip for
     * anything else, so the card came out uncategorised while every other check announced itself. A
     * stolen-handset register belongs with the remote-lock check regardless, and inventing a fifth
     * category for one row would have been the worse fix.
     */
    const val CHECK_ID: String = "security.imei_checksum"

    private const val TITLE = "IMEI checksum"

    /**
     * Always present, on every outcome including a pass.
     *
     * The blocklist question is open no matter what the arithmetic says, so the instruction to go and
     * settle it does not belong only next to a bad result. A pass that quietly stopped mentioning it
     * would be the exact "green tick means not stolen" reading this check is written to prevent.
     */
    private const val CHECK_THE_BLOCKLIST =
        "Check it against the government CEIR portal before you pay — that is the only place that " +
            "knows whether a handset has been reported lost or stolen. On a dual-SIM phone, dial " +
            "*#06# and check both numbers."

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A single mistyped or transposed digit fails the checksum exactly like a fabricated number.",
        "Copying from a worn sticker confuses 8 with B, 0 with O, and 1 with 7.",
        "Some very old or grey-market handsets shipped with genuinely malformed numbers.",
    )

    fun evaluate(imei: Imei): CheckResult {
        if (imei.length == 0) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "No IMEI entered yet.",
                action = "Dial *#06# on the phone, or look in Settings, About phone.",
                // No consequence: nothing has been measured, so there is nothing to consequence about.
            )
        }

        if (!imei.isComplete) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "That is ${imei.length} digits. An IMEI has ${Imei.FULL_LENGTH}.",
                action = "Dial *#06# and copy all ${Imei.FULL_LENGTH} digits. " + CHECK_THE_BLOCKLIST,
                measurements = listOf(
                    Measurement("Entered", "${imei.length}", "of ${Imei.FULL_LENGTH} digits"),
                ),
            )
        }

        // Note what is *not* here: the IMEI itself.
        //
        // The render showed the same fifteen digits three times on one screen — in the field, in the
        // large grouped line under it, and again in this table a hundred pixels below that. The
        // grouped line is the one that earns its place, because comparing against a worn sticker is
        // easier in large tabular digits. If this check is ever added to a saved report, where the
        // screen is not there to carry it, the row comes back with that work.
        val measurements = buildList {
            imei.typeAllocationCode?.let { add(Measurement("Model code", it)) }
        }

        if (!imei.isValid) {
            // CAUTION, never FAIL, and the reason is the false-positive list rather than timidity. A
            // failed checksum is consistent with a cloned handset *and* with one fat-fingered digit,
            // and the app cannot tell which. A FAIL here would accuse a seller on the strength of a
            // typing error, which is the one mistake this product treats as unforgivable.
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Those fifteen digits are not a valid IMEI.",
                consequence = "Either a digit was copied wrong, or the number was never issued. A " +
                    "handset showing an invented IMEI has usually had its identity changed, which is " +
                    "what happens to a phone that needs to stop matching a stolen-goods report.",
                action = "Type it again straight from *#06#, carefully. If it still fails, treat the " +
                    "phone as unidentified and walk away. " + CHECK_THE_BLOCKLIST,
                measurements = measurements + Measurement(
                    "Last digit",
                    "${imei.givenCheckDigit} — expected ${imei.expectedCheckDigit}",
                ),
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        // A pass, and carefully only about the arithmetic.
        //
        // Confidence is HIGH because the thing measured — a Luhn checksum over fourteen digits — is
        // exact and fully determined. That is not a claim about the phone, and the headline says so in
        // the same breath, because the alternative is a buyer walking away believing the app cleared a
        // handset it never looked up.
        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = "A well-formed IMEI. This does not tell you whether the phone is blocked.",
            action = CHECK_THE_BLOCKLIST,
            measurements = measurements,
        )
    }
}
