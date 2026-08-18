package com.phoneproof.core.preferences.passes

/**
 * The code a buyer types onto the phone they are inspecting.
 *
 * `PP-XXXX-XXXX`: eight characters, the last of which is a check character. Codes are **issued by the
 * server**, never by the app — an app that could mint valid codes would be giving the product away. What
 * lives here is only the ability to read one back, and to reject a typo without asking the network.
 *
 * ## Why a check character
 *
 * The person typing is standing in front of a seller who wants their phone back. A wrong code has to fail
 * **immediately and locally**, saying "that is not a code" — not after a round trip that might also fail for
 * signal reasons, leaving them unsure which of the two went wrong. One character buys that.
 *
 * ## Why this alphabet
 *
 * Crockford's Base32: the digits and the letters, minus `I`, `L`, `O` and `U`. The first three are dropped
 * because they are indistinguishable from `1`, `1` and `0` in most fonts and on every cracked screen this app
 * will run on. `U` is dropped so a randomly generated code cannot spell something unfortunate.
 *
 * Reading is deliberately forgiving in the way Crockford specifies: `O` is read as `0`, `I` and `L` as `1`,
 * and case is ignored. Someone copying a screenshot by eye should not be punished for a distinction the
 * alphabet exists to avoid.
 *
 * ## This algorithm exists twice
 *
 * The server issues codes in JavaScript; this reads them in Kotlin. Two implementations of one algorithm
 * drift, and the failure would be silent and awful — a code the buyer paid for that the app calls invalid. So
 * both are tested against one committed file, `licensing/code-test-vectors.txt`. Change the algorithm and you
 * regenerate that file; never let one side quietly "correct" a vector.
 */
object PassCode {

    /** Crockford Base32, in the order that defines each character's value. */
    const val ALPHABET: String = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** Every code starts with this, so a buyer can tell at a glance what they are looking at. */
    const val PREFIX: String = "PP"

    /** Characters after the prefix, including the check character. */
    const val BODY_LENGTH: Int = 8

    /**
     * Turns whatever the buyer typed into a comparable form, or null if it cannot be one.
     *
     * Accepts the shape people actually produce: lower case, missing hyphens, extra spaces, a stray `O` for
     * a zero. Rejects anything that is not eight body characters after an optional `PP`.
     *
     * Returns the canonical form — upper case, no separators, prefix stripped — so callers compare like with
     * like. [format] puts it back into something a human reads.
     */
    fun normalise(typed: String): String? {
        val cleaned = buildString {
            typed.forEach { character ->
                when (val upper = character.uppercaseChar()) {
                    // Separators and whitespace carry no meaning; a code read aloud gains and loses them.
                    '-', ' ', '\t', '\n', '\r', '_' -> Unit
                    // Crockford's substitutions, which are the whole reason those letters are absent.
                    'O' -> append('0')
                    'I', 'L' -> append('1')
                    else -> append(upper)
                }
            }
        }

        val body = cleaned.removePrefix(PREFIX)
        if (body.length != BODY_LENGTH) return null
        if (body.any { it !in ALPHABET }) return null
        return body
    }

    /**
     * Whether [typed] is a code this app could have been given.
     *
     * Says nothing about whether it has passes left, or ever existed — only the server knows that. This is
     * the offline gate that stops a typo becoming a network round trip and a confusing error.
     */
    fun isWellFormed(typed: String): Boolean {
        val body = normalise(typed) ?: return false
        return body.last() == checkCharacter(body.dropLast(1))
    }

    /**
     * The check character for a payload of [BODY_LENGTH] - 1 characters.
     *
     * A position-weighted sum, modulo the alphabet size. Weighted by position on purpose: an unweighted sum
     * would accept every transposition, and swapping two adjacent characters is the second commonest typing
     * mistake after getting one character wrong.
     *
     * ## Why the weights are odd, and what that costs
     *
     * The weights are 1, 3, 5, … — all **odd**, which is not cosmetic. The alphabet has 32 characters, so the
     * modulus is a power of two, and an odd number is invertible modulo a power of two. That makes
     * `difference × weight` impossible to land on zero for any non-zero difference, which means **every
     * single-character error is caught**. With the obvious weights 2, 3, 4, … twelve single-character errors
     * slip through — a mistyped code the app would accept and then send to the server to be refused, which is
     * the outcome the check character exists to avoid.
     *
     * The cost is that adjacent transpositions are caught only when the two characters differ by something
     * other than exactly 16. Six combinations out of 186 slip through — `0` swapped with `G`, and its
     * relatives. This is not a shortcoming that can be tuned away: with 32 symbols, catching every
     * single-character error and every transposition at once is impossible for a weighted-sum check, because
     * the difference of two odd weights is always even. Measured both ways before choosing, and single-
     * character errors are both commoner and worse, so they are the ones eliminated.
     *
     * None of this is a security boundary. A determined person can compute a valid-looking code; the server
     * decides whether one exists and has passes left. This exists so a **typo** fails in the buyer's hand
     * rather than over the network.
     */
    fun checkCharacter(payload: String): Char {
        var sum = 0
        payload.forEachIndexed { index, character ->
            val value = ALPHABET.indexOf(character)
            require(value >= 0) { "'$character' is not in the pass-code alphabet" }
            sum += value * (2 * index + 1)
        }
        return ALPHABET[sum % ALPHABET.length]
    }

    /** `PP-XXXX-XXXX`, for showing to a person. Input may be canonical or already formatted. */
    fun format(code: String): String? {
        val body = normalise(code) ?: return null
        return "$PREFIX-${body.take(4)}-${body.drop(4)}"
    }
}
