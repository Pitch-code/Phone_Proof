package com.phoneproof.core.preferences.passes

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The pass-code reader, checked against the file the server is also checked against.
 *
 * The vectors are not decoration. The app rejects typos offline, which means the checksum exists twice — here
 * in Kotlin and in JavaScript on the licence server — and two implementations of one algorithm drift. The
 * failure mode is the worst kind: a code somebody paid for that the app calls invalid, at the moment they are
 * standing in front of a seller. One committed file, both sides tested against it.
 */
class PassCodeTest {

    private data class Vector(val input: String, val valid: Boolean, val canonical: String?)

    private val vectors: List<Vector> by lazy {
        val root = System.getProperty("phoneproof.repoRoot")
        assertThat(root).isNotNull()

        val file = File(root, "licensing/code-test-vectors.txt")
        assertThat(file.isFile).isTrue()

        file.readLines().mapNotNull { line ->
            // Comments and blank lines. A line that is only a comment starts with '#'; a trailing comment
            // after the fields is allowed and ignored.
            if (line.isBlank() || line.trimStart().startsWith("#")) return@mapNotNull null

            val fields = line.split('\t')
            if (fields.size < 2) return@mapNotNull null

            Vector(
                input = fields[0],
                valid = fields[1].trim() == "VALID",
                canonical = fields.getOrNull(2)?.trim()?.takeIf { it != "-" },
            )
        }
    }

    @Test
    fun the_vector_file_is_actually_being_read() {
        // Guards the whole file: a parser that silently found nothing would make every test below pass by
        // iterating over an empty list, which is the failure that hides all the others.
        assertThat(vectors).isNotEmpty()
        assertThat(vectors.count { it.valid }).isAtLeast(10)
        assertThat(vectors.count { !it.valid }).isAtLeast(8)
    }

    @Test
    fun every_vector_agrees_with_this_implementation() {
        val disagreements = vectors.filter { PassCode.isWellFormed(it.input) != it.valid }

        // Named rather than counted, because when this fails the useful information is *which* code and in
        // which direction — a valid code being rejected is a buyer losing money, a bad one being accepted is
        // only a wasted round trip.
        assertThat(
            disagreements.map { "'${it.input}' should be ${if (it.valid) "VALID" else "INVALID"}" },
        ).isEmpty()
    }

    @Test
    fun a_valid_vector_normalises_to_the_canonical_body_it_names() {
        vectors.filter { it.valid && it.canonical != null }.forEach { vector ->
            assertThat(PassCode.normalise(vector.input)).isEqualTo(vector.canonical)
        }
    }

    @Test
    fun the_forgiving_readings_all_land_on_the_same_code() {
        // Someone copying a code off a screenshot by eye, in a hurry, on a cracked screen. Every one of these
        // is the same code and must be treated as such.
        val canonical = PassCode.normalise("PP-N6WE-DKZE")
        assertThat(canonical).isNotNull()

        listOf(
            "pp-n6we-dkze",
            "  PP N6WE DKZX  ",
            "PP_N6WE_DKZX",
            "N6WEDKZE",
            "ppn6wedkze",
        ).forEach { typed ->
            assertThat(PassCode.normalise(typed)).isEqualTo(canonical)
        }
    }

    @Test
    fun the_letters_the_alphabet_leaves_out_are_read_as_their_digits() {
        // The reason those letters are absent: on the handsets this app is aimed at they are the same glyph.
        assertThat(PassCode.normalise("PP-O123-4567")).isEqualTo("01234567")
        assertThat(PassCode.normalise("PP-I234-5678")).isEqualTo("12345678")
        assertThat(PassCode.normalise("PP-L234-5678")).isEqualTo("12345678")
    }

    @Test
    fun the_alphabet_has_no_ambiguous_characters_in_it() {
        // Stated here so that "adding a couple more characters for entropy" fails rather than quietly making
        // codes harder to read.
        assertThat(PassCode.ALPHABET).hasLength(32)
        listOf('I', 'L', 'O', 'U').forEach { excluded ->
            assertThat(PassCode.ALPHABET).doesNotContain(excluded.toString())
        }
        // No character twice, or two payloads would share a checksum for no reason.
        assertThat(PassCode.ALPHABET.toSet()).hasSize(PassCode.ALPHABET.length)
    }

    @Test
    fun swapping_two_adjacent_characters_is_caught_unless_they_are_half_the_alphabet_apart() {
        // Position weighting exists for this: an unweighted sum would accept every transposition, and it is
        // the second commonest typing mistake.
        //
        // The exception is real and documented rather than hidden. With 32 characters the modulus is a power
        // of two, and the difference between two odd weights is always even — so a pair whose values are
        // exactly 16 apart survives being swapped. Six combinations out of 186. Catching those as well would
        // mean giving up catching every single-character error, which is both commoner and worse.
        val body = "N6WEDKZE"
        var caught = 0
        var tried = 0
        val exempt = mutableListOf<String>()

        for (index in 0 until 6) {
            if (body[index] == body[index + 1]) continue
            val gap = PassCode.ALPHABET.indexOf(body[index]) -
                PassCode.ALPHABET.indexOf(body[index + 1])

            val swapped = body.take(index) + body[index + 1] + body[index] + body.drop(index + 2)

            if (kotlin.math.abs(gap) == PassCode.ALPHABET.length / 2) {
                exempt += swapped
                continue
            }
            tried++
            if (!PassCode.isWellFormed(swapped)) caught++
        }

        assertThat(tried).isAtLeast(4)
        assertThat(caught).isEqualTo(tried)
        exempt.forEach { assertThat(PassCode.isWellFormed(it)).isTrue() }
    }

    @Test
    fun the_transposition_gap_is_demonstrated_rather_than_only_described() {
        // A comment claiming a limitation is not evidence of one. `0` and `G` sit exactly 16 apart, so
        // swapping them survives the check — constructed here rather than hunted for, so the claim in
        // `checkCharacter` is held to an actual example.
        //
        // If the algorithm is ever improved to close this, THIS test fails, which is the point: someone will
        // then delete it and the honest documentation with it, rather than leaving prose describing a
        // weakness that no longer exists.
        val payload = "0G23456"
        val code = payload + PassCode.checkCharacter(payload)
        val swapped = "G023456" + code.last()

        assertThat(PassCode.ALPHABET.indexOf('G') - PassCode.ALPHABET.indexOf('0')).isEqualTo(16)
        assertThat(PassCode.isWellFormed(code)).isTrue()
        assertThat(PassCode.isWellFormed(swapped)).isTrue()
    }

    @Test
    fun changing_any_single_character_is_caught() {
        // A modulo-32 check character cannot catch everything, but it must catch every single-character
        // error, which is the commonest of all.
        val body = "N6WEDKZE"
        val payload = body.dropLast(1)

        payload.indices.forEach { index ->
            PassCode.ALPHABET.forEach { replacement ->
                if (replacement == payload[index]) return@forEach
                val broken = payload.take(index) + replacement + payload.drop(index + 1) + body.last()
                assertThat(PassCode.isWellFormed(broken)).isFalse()
            }
        }
    }

    @Test
    fun format_produces_something_a_person_can_read_back() {
        assertThat(PassCode.format("n6wedkze")).isEqualTo("PP-N6WE-DKZE")
        // Idempotent, so a formatted code can be handed round without accumulating hyphens.
        assertThat(PassCode.format("PP-N6WE-DKZE")).isEqualTo("PP-N6WE-DKZE")
        // Wrong shape has nothing to format.
        assertThat(PassCode.format("hello")).isNull()
        assertThat(PassCode.format("PP-ABCD-EFGU")).isNull()
    }

    @Test
    fun formatting_is_about_shape_and_says_nothing_about_validity() {
        // Worth pinning, because it is a boundary that would otherwise be guessed at. "nonsense" happens to
        // normalise to eight legal characters — N0NSENSE, since O reads as 0 — so it formats perfectly well
        // and is still not a code anyone was issued.
        //
        // I got this wrong first time and asserted format() would reject it. Keeping the real behaviour
        // written down: presentation and validity are different questions, and only `isWellFormed` answers
        // the second.
        assertThat(PassCode.format("nonsense")).isEqualTo("PP-N0NS-ENSE")
        assertThat(PassCode.isWellFormed("nonsense")).isFalse()
    }

    @Test
    fun nothing_the_app_can_do_produces_a_valid_code() {
        // The app must never be able to mint one; only the server may. This is not enforceable by a test, but
        // it is worth recording that the only public entry points read codes rather than create them —
        // checkCharacter is exposed for the vector file's sake and is useless without a payload to sign.
        val methods = PassCode::class.java.declaredMethods.map { it.name }
        assertThat(methods).doesNotContain("generate")
        assertThat(methods).doesNotContain("issue")
        assertThat(methods).doesNotContain("mint")
    }
}
