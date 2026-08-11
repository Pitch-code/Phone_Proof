package com.phoneproof.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PluralTest {

    @Test
    fun `one is singular`() {
        assertThat(plural(1, "month")).isEqualTo("1 month")
        assertThat(plural(1, "app")).isEqualTo("1 app")
    }

    @Test
    fun `zero is plural, which is correct in English`() {
        assertThat(plural(0, "month")).isEqualTo("0 months")
    }

    @Test
    fun `many is plural`() {
        assertThat(plural(35, "month")).isEqualTo("35 months")
    }

    @Test
    fun `an irregular plural can be supplied`() {
        assertThat(plural(1, "patch", "patches")).isEqualTo("1 patch")
        assertThat(plural(3, "patch", "patches")).isEqualTo("3 patches")
    }

    @Test
    fun `a multi word noun pluralises on the last word`() {
        assertThat(plural(1, "more area")).isEqualTo("1 more area")
        assertThat(plural(2, "more area")).isEqualTo("2 more areas")
    }

    @Test
    fun `nounFor returns the word alone for sentences that place the number elsewhere`() {
        assertThat(nounFor(1, "month")).isEqualTo("month")
        assertThat(nounFor(4, "month")).isEqualTo("months")
    }

    @Test
    fun `minus one is singular too`() {
        // Guards the unit label when a patch date is a day or two in the future and the computed
        // age rounds to -1: "-1 months" would look like a bug on screen.
        assertThat(nounFor(-1, "month")).isEqualTo("month")
    }
}
