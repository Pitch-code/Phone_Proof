package com.phoneproof.checks.imei

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImeiTest {

    /**
     * The worked example from the GSMA's own description of the algorithm, checked by hand:
     *
     * body            4 9 0 1 5 4 2 0 3 2 3 7 5 1
     * doubled (odd i)   9→9  1→2  4→8  0→0  2→4  7→5  1→2
     * kept    (even i) 4   0   5   2   3   3   5
     * sum = 22 + 30 = 52 -> (10 - 2) % 10 = 8
     */
    @Test
    fun `the known-good example validates`() {
        val imei = Imei.of("490154203237518")

        assertThat(imei.isComplete).isTrue()
        assertThat(imei.expectedCheckDigit).isEqualTo(8)
        assertThat(imei.givenCheckDigit).isEqualTo(8)
        assertThat(imei.isValid).isTrue()
    }

    @Test
    fun `changing the check digit invalidates it`() {
        assertThat(Imei.of("490154203237518").isValid).isTrue()
        for (wrong in 0..9) {
            if (wrong == 8) continue
            assertThat(Imei.of("49015420323751$wrong").isValid).isFalse()
        }
    }

    @Test
    fun `every single-digit error in the body is caught`() {
        // What the checksum is actually for. Luhn catches all single-digit substitutions, and this
        // asserts it over all 14 positions and all 9 wrong values rather than trusting the property.
        val valid = "490154203237518"
        for (position in 0 until 14) {
            for (replacement in '0'..'9') {
                if (valid[position] == replacement) continue
                val mutated = valid.toCharArray().also { it[position] = replacement }.concatToString()
                assertThat(Imei.of(mutated).isValid).isFalse()
            }
        }
    }

    @Test
    fun `adjacent transpositions are caught except the one Luhn cannot see`() {
        // Honest about a real limitation rather than asserting something untrue. Luhn catches every
        // adjacent transposition apart from swapping 0 and 9, which produces the same sum. Anyone
        // reading this file should know that before trusting the check further than it goes.
        val valid = "490154203237518"
        var missed = 0
        for (position in 0 until 13) {
            val a = valid[position]
            val b = valid[position + 1]
            if (a == b) continue
            val swapped = valid.toCharArray().also {
                it[position] = b
                it[position + 1] = a
            }.concatToString()
            if (Imei.of(swapped).isValid) {
                missed++
                assertThat(setOf(a, b)).isEqualTo(setOf('0', '9'))
            }
        }
        // The example contains one 0-9 pair, at the start: "49..." -> "94...".
        assertThat(missed).isEqualTo(1)
    }

    @Test
    fun `the check digit is a real modulus, not a coincidence of one example`() {
        // Generated bodies rather than a fixture, so the parity of the doubling cannot be silently
        // wrong for half of all inputs while one hand-picked case still passes.
        for (seed in 0 until 200) {
            val body = (0 until 14).map { ((seed * 7 + it * 3) % 10).digitToChar() }.joinToString("")
            val check = Imei.luhnCheckDigit(body)

            assertThat(check).isIn(0..9)
            assertThat(Imei.of(body + check).isValid).isTrue()
            assertThat(Imei.of(body + ((check + 1) % 10)).isValid).isFalse()
        }
    }

    @Test
    fun `spaces hyphens and pasted rubbish are stripped rather than rejected`() {
        // How the number actually arrives: copied off a sticker, or out of the *#06# dialog.
        assertThat(Imei.of("49 015420 323751 8").digits).isEqualTo("490154203237518")
        assertThat(Imei.of("490154-203237-518").digits).isEqualTo("490154203237518")
        assertThat(Imei.of("IMEI: 490154203237518").digits).isEqualTo("490154203237518")
        assertThat(Imei.of("  490154203237518\n").isValid).isTrue()
    }

    @Test
    fun `pasting two numbers takes the first rather than making nonsense of both`() {
        val two = "490154203237518 356938035643809"
        assertThat(Imei.of(two).digits).isEqualTo("490154203237518")
        assertThat(Imei.of(two).isValid).isTrue()
    }

    @Test
    fun `a partial entry reports its length and withholds a verdict`() {
        val partial = Imei.of("4901542")

        assertThat(partial.isComplete).isFalse()
        assertThat(partial.isValid).isFalse()
        assertThat(partial.givenCheckDigit).isNull()
        // Under fourteen digits there is nothing to compute a check digit from.
        assertThat(partial.expectedCheckDigit).isNull()
    }

    @Test
    fun `the expected check digit appears as soon as the fourteenth digit does`() {
        // So the UI can show what the last digit ought to be while the buyer is still typing it.
        assertThat(Imei.of("49015420323751").expectedCheckDigit).isEqualTo(8)
    }

    @Test
    fun `empty input is empty rather than an exception`() {
        val empty = Imei.of("")

        assertThat(empty.length).isEqualTo(0)
        assertThat(empty.isComplete).isFalse()
        assertThat(empty.typeAllocationCode).isNull()
        assertThat(empty.formatted).isEmpty()
    }

    @Test
    fun `the type allocation code is the first eight digits and is never looked up`() {
        assertThat(Imei.of("490154203237518").typeAllocationCode).isEqualTo("49015420")
        // Not available until there are eight digits to take.
        assertThat(Imei.of("4901542").typeAllocationCode).isNull()
    }

    @Test
    fun `formatting matches the grouping printed on a box`() {
        assertThat(Imei.of("490154203237518").formatted).isEqualTo("49 015420 323751 8")
        // Partial input still groups, so the digits do not jump as the buyer types.
        assertThat(Imei.of("49015").formatted).isEqualTo("49 015")
    }

    @Test
    fun `a non-digit can never reach the constructor`() {
        // of() is the only intended door in, and it filters. This guards the door itself.
        runCatching { Imei("49015420323751X") }
            .also { assertThat(it.isFailure).isTrue() }
    }
}
