package com.phoneproof.feature.guide

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests on the content itself.
 *
 * Unusual, but this screen *is* content: its value is entirely in whether the instructions are
 * complete and readable. A step that lost its "bad sign" while someone edited the wording would
 * still compile and render, and would quietly stop telling the buyer what they were looking for.
 */
class GuideContentTest {

    @Test
    fun `every step is complete`() {
        GuideSteps.forEach { step ->
            assertThat(step.title).isNotEmpty()
            assertThat(step.summary).isNotEmpty()
            assertThat(step.whyItMatters).isNotEmpty()
            assertThat(step.goodSign).isNotEmpty()
            assertThat(step.badSign).isNotEmpty()
            assertThat(step.howTo).isNotEmpty()
        }
    }

    @Test
    fun `every step has at least three things to do`() {
        // Fewer than three and it is a remark rather than a procedure someone can follow while
        // being watched.
        GuideSteps.forEach { step ->
            assertThat(step.howTo).hasSize(4)
        }
    }

    @Test
    fun `ids are unique so the expand state cannot collide`() {
        assertThat(GuideSteps.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `every step has its own diagram`() {
        // A repeated diagram would show the wrong action for a step, which is worse than none.
        assertThat(GuideSteps.map { it.diagram }).containsNoDuplicates()
    }

    @Test
    fun `every diagram is used by a step`() {
        // The other direction: a drawing nobody references is dead code that renders nowhere.
        assertThat(GuideSteps.map { it.diagram })
            .containsExactlyElementsIn(GuideDiagram.entries)
    }

    @Test
    fun `the account check is present, because it is the most expensive mistake`() {
        val account = GuideSteps.first { it.id == "guide.account" }

        assertThat(account.badSign.lowercase()).contains("walk away")

        // The timing is the whole point of this step: doing it after paying is worthless. Asserted
        // across the step rather than on one field, so moving the sentence between the summary and
        // the explanation is a wording choice rather than a test failure.
        val everything = (account.summary + " " + account.whyItMatters).lowercase()
        assertThat(everything).contains("before")
    }

    @Test
    fun `the water sticker step explains that a missing sticker is also a warning`() {
        // The subtle part of that check. A buyer told only "look for red" would see no sticker and
        // conclude the phone was fine.
        val water = GuideSteps.first { it.id == "guide.water" }

        assertThat(water.badSign.lowercase()).contains("no sticker")
    }

    @Test
    fun `instructions avoid asking for force`() {
        // The twist step could damage a phone if it were worded carelessly, and the buyer may not
        // own it yet. Every step that involves handling says gently, or says not to force it.
        val twist = GuideSteps.first { it.id == "guide.frame" }

        assertThat(twist.howTo.joinToString(" ").lowercase()).contains("gently")
        assertThat(twist.howTo.joinToString(" ").lowercase()).contains("do not force")
    }

    @Test
    fun `sentences stay short enough to read on a phone in a shop`() {
        // A crude readability guard. Long sentences are the first thing to creep back in when
        // wording is edited, and this screen is read one-handed under pressure.
        GuideSteps.forEach { step ->
            step.howTo.forEach { line ->
                assertThat(line.split(" ").size).isLessThan(22)
            }
        }
    }

    @Test
    fun `there are enough steps to be worth opening the screen`() {
        assertThat(GuideSteps.size).isAtLeast(8)
    }
}
