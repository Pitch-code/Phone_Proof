package com.phoneproof.core.run

/**
 * What a step asks of the buyer, which is the only thing that governs where it sits in the run.
 *
 * The person using this app is standing in a shop with the seller watching them, and every kind of
 * effort below has a different social cost. Waiting for a scan costs nothing. Dragging a finger over
 * the whole screen costs a little patience. Asking the seller to repeat what they advertised, or to
 * read out the IMEI, costs the buyer some nerve — so those come after the phone has already earned
 * the buyer's attention by finding something.
 */
enum class StepEffort {
    /** The phone measures itself and the buyer waits. */
    AUTOMATIC,

    /** The buyer has to do something with the handset: drag, listen, aim it at something. */
    HANDS_ON,

    /** Needs a number or a claim out of the seller's mouth. */
    ASK_THE_SELLER,

    /** No measurement at all — the app tells the buyer where to look and they look. */
    LOOK_YOURSELF,
}

/**
 * Something a step needs from the room, not from the phone.
 *
 * Held as data rather than prose so the run can warn about it once, up front, instead of a screen
 * discovering mid-measurement that the conditions were never right. A tone played into a noisy shop
 * and a colour page inspected under a showroom light both produce a confident wrong answer, which
 * this project treats as worse than no answer at all.
 */
enum class RunCondition {
    /** A quiet spot. The loopback tone is measured on the microphone. */
    QUIET,

    /** Shade or a cupped hand. Dead pixels hide under a bright showroom light. */
    DIM_LIGHT,

    /**
     * No heads-up notifications.
     *
     * A real one, seen in testing: a WhatsApp banner landed on top of the touch grid. It is a system
     * window, so the touches it swallowed never reached the app and read as a dead strip.
     */
    NO_INTERRUPTIONS,
}

/**
 * One stop on the guided run.
 *
 * [id] is deliberately the navigation route of the screen that performs the step. The alternative
 * was a lookup table mapping step ids to routes, which is one more thing to keep in sync and one
 * more place for a typo to become a dead button. The app module owns navigation and asserts in a
 * test that every id here resolves to a real destination, the same arrangement already used for the
 * saved-reports directory name.
 */
data class RunStep(
    val id: String,
    val title: String,
    /** One line on why a buyer should care. Shown under the title on the checklist. */
    val why: String,
    val effort: StepEffort,
    val needs: Set<RunCondition> = emptySet(),
    /** Rough wall-clock seconds, so the run can say how long it will take before it starts. */
    val typicalSeconds: Int,
    /**
     * Whether a clean verdict is allowed without it.
     *
     * Skipping an essential step does not hide the faults that were found — it only stops the run
     * claiming the phone looks good, because it did not look. See [RunVerdict].
     */
    val essential: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "RunStep.id must not be blank" }
        require(typicalSeconds > 0) { "'$id' needs a positive duration to be worth announcing" }
    }
}
