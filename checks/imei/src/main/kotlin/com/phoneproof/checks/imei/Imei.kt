package com.phoneproof.checks.imei

/**
 * An IMEI, and the only thing about it an app can actually establish: whether the number is
 * arithmetically well-formed.
 *
 * ## Why the buyer types it
 *
 * The app cannot read it. `READ_PRIVILEGED_PHONE_STATE` has been required since Android 10, on every
 * Android 10+ device regardless of target SDK, and it is granted only to platform-signed and
 * privileged system apps. A third-party app asking gets a `SecurityException`. So requesting
 * `READ_PHONE_STATE` would gain nothing and look alarming, and there is no reader class anywhere in
 * this module for the same reason.
 *
 * ## What a valid checksum does and does not prove
 *
 * The last of the fifteen digits is a Luhn check digit over the first fourteen. That makes this a
 * **typo detector, and a detector of numbers that were never issued** — a fabricated IMEI stuck on a
 * cloned handset usually fails it, because whoever invented the number did not run the algorithm.
 *
 * It proves nothing else. In particular it says nothing about whether the phone is stolen or blocked,
 * which is a question about a national database rather than about arithmetic. Everything in this file
 * is worded to keep those two apart, because a green tick next to the word IMEI would be read as "not
 * stolen" by every buyer who saw it, and that is a claim the app is in no position to make.
 */
data class Imei(val digits: String) {

    init {
        require(digits.all(Char::isDigit)) { "an Imei holds digits only, was '$digits'" }
    }

    val length: Int get() = digits.length

    val isComplete: Boolean get() = length == FULL_LENGTH

    /** The check digit as typed: the fifteenth. Null until fifteen digits exist. */
    val givenCheckDigit: Int?
        get() = if (isComplete) digits.last().digitToInt() else null

    /** The check digit the first fourteen digits imply. Null until there are fourteen to work from. */
    val expectedCheckDigit: Int?
        get() = if (length >= BODY_LENGTH) luhnCheckDigit(digits.take(BODY_LENGTH)) else null

    val isValid: Boolean
        get() = isComplete && givenCheckDigit == expectedCheckDigit

    /**
     * The Type Allocation Code: the first eight digits, which identify the model.
     *
     * Exposed as a fact and deliberately **not** looked up. Resolving a TAC to a model name would
     * need either a bundled table, which goes stale and is what makes a competitor report a new
     * handset as having 3 GB of RAM, or a network call, in an app that has to work in a basement with
     * no signal. It is shown so a buyer can compare it against a second IMEI on the same phone — a
     * dual-SIM handset's two IMEIs share a TAC, and two that disagree is worth asking about.
     */
    val typeAllocationCode: String?
        get() = if (length >= TAC_LENGTH) digits.take(TAC_LENGTH) else null

    /** Grouped 2-6-6-1, the grouping used on the box and by `*#06#`, so the two can be compared. */
    val formatted: String
        get() = buildString {
            digits.forEachIndexed { index, digit ->
                if (index == 2 || index == 8 || index == 14) append(' ')
                append(digit)
            }
        }

    companion object {
        const val FULL_LENGTH: Int = 15
        private const val BODY_LENGTH: Int = 14
        private const val TAC_LENGTH: Int = 8

        /**
         * Builds an [Imei] from whatever the buyer typed or pasted.
         *
         * Everything that is not a digit is dropped rather than rejected. People paste IMEIs with
         * spaces from `*#06#`, with hyphens from a box, and with a stray trailing character from a
         * selection that grabbed one too many — refusing those would be pedantry aimed at the one
         * screen where the buyer is copying fifteen digits by hand under time pressure.
         *
         * Truncated to fifteen digits, so pasting a block containing two IMEIs takes the first rather
         * than silently producing a number that is nonsense.
         */
        fun of(raw: String): Imei = Imei(raw.filter(Char::isDigit).take(FULL_LENGTH))

        /**
         * The Luhn check digit for a run of digits.
         *
         * Doubling starts at the **second digit from the left** for a 14-digit body, which is the
         * same thing as "every second digit from the right" once the check digit is included. Written
         * left to right because that is the order the digits arrive in, and stated here because
         * getting the parity backwards produces a function that is wrong for exactly half of all
         * inputs — which is the kind of bug that passes a single hand-picked test.
         */
        fun luhnCheckDigit(body: String): Int {
            var sum = 0
            body.forEachIndexed { index, char ->
                val digit = char.digitToInt()
                sum += if (index % 2 == 1) {
                    val doubled = digit * 2
                    if (doubled > 9) doubled - 9 else doubled
                } else {
                    digit
                }
            }
            return (10 - sum % 10) % 10
        }
    }
}
