package com.phoneproof.core.run

/**
 * The order the phone gets tested in, and the reasoning for it.
 *
 * Until now the app had eight checks on Home and no opinion about which to run first, so the buyer
 * had to design their own inspection while a seller watched. The order below is the product, as much
 * as any individual check is:
 *
 *  1. **Automatic first.** The instant scan needs no instructions and returns in seconds, so the
 *     buyer gets something to look at before they have had to ask the seller for anything. It also
 *     carries the deal-breakers — remote lock, root, a tampered build — and there is no sense
 *     spending eight minutes on a handset a lender can brick after it is paid for.
 *  2. **The two screen tests together.** Both need the whole display and both are ruined by a
 *     notification banner, so the run asks for quiet once and then does them back to back rather
 *     than interrupting twice.
 *  3. **The noisy one next**, while the buyer is still holding the phone to their face anyway.
 *  4. **Then the seller.** Claims and the IMEI need a person to answer, which is the slowest and most
 *     awkward part of any inspection. By this point the app has usually found something, which is
 *     exactly the leverage that makes those questions easier to ask.
 *  5. **Eyes last.** The walkthrough is the longest step and the only one where the app measures
 *     nothing, so it goes where a buyer can stop early without losing a measurement.
 *
 * The remote-lock check is not a step of its own: it runs inside the instant scan, which is why
 * `security.device_admin_lock` appears in that step's results. Home still offers it separately for
 * someone who only wants that one answer.
 */
object RunPlan {

    val steps: List<RunStep> = listOf(
        RunStep(
            id = "scan",
            title = "Instant scan",
            why = "Software, storage, sensors, battery — and whether a lender can still lock it",
            effort = StepEffort.AUTOMATIC,
            typicalSeconds = 20,
        ),
        RunStep(
            id = "touch",
            title = "Touch response",
            why = "Dead patches, including along the edges where cracks start",
            effort = StepEffort.HANDS_ON,
            needs = setOf(RunCondition.NO_INTERRUPTIONS),
            typicalSeconds = 60,
        ),
        // Immediately after touch coverage, because it is the same glass and the same hand position — and
        // because the two faults are complements: coverage finds dead areas, this finds dead capacity.
        RunStep(
            id = "multi-touch",
            title = "Fingers at once",
            why = "A screen can respond everywhere and still lose the fourth finger",
            effort = StepEffort.HANDS_ON,
            needs = setOf(RunCondition.NO_INTERRUPTIONS),
            typicalSeconds = 30,
        ),
        RunStep(
            id = "screen-patterns",
            title = "Dead pixels and burn-in",
            why = "Plain colours make stuck pixels and a ghosted status bar obvious",
            effort = StepEffort.HANDS_ON,
            needs = setOf(RunCondition.NO_INTERRUPTIONS, RunCondition.DIM_LIGHT),
            typicalSeconds = 75,
        ),
        RunStep(
            id = "audio",
            title = "Microphone, earpiece and speaker",
            // Three parts, and the earpiece is the one worth naming: it is the only speaker on a phone
            // that nothing in a shop exercises, so it is the one a buyer discovers on their first call.
            why = "Three separate parts — including the earpiece, which nothing else in a shop tests",
            effort = StepEffort.HANDS_ON,
            needs = setOf(RunCondition.QUIET),
            typicalSeconds = 45,
        ),
        RunStep(
            id = "camera",
            title = "Cameras and flashlight",
            why = "Every lens has to produce a live picture, not just open",
            effort = StepEffort.HANDS_ON,
            typicalSeconds = 40,
        ),
        // Straight after the camera because it is the other test done with the phone held up and turned
        // about, and because it asks for no permission at all — a good thing to reach while the buyer is
        // still saying yes to things.
        // Grouped with the sensor test: both are hands-on, both need no permission, and both are things
        // the buyer does to the outside of the phone rather than to the screen.
        RunStep(
            id = "volume-buttons",
            title = "Volume buttons",
            why = "A jammed volume key makes a phone boot into recovery on its own",
            effort = StepEffort.HANDS_ON,
            typicalSeconds = 20,
        ),
        // Right after the buttons: both are things done to the outside of the phone, and this one asks the
        // buyer for nothing at all beyond holding still.
        RunStep(
            id = "vibration",
            title = "Vibration",
            why = "Measured with the accelerometer, so nobody has to be asked if they felt it",
            effort = StepEffort.HANDS_ON,
            typicalSeconds = 20,
        ),
        RunStep(
            id = "sensors",
            title = "Sensors that still work",
            why = "Tilt and cover the phone: a dead sensor is still on the parts list",
            effort = StepEffort.HANDS_ON,
            typicalSeconds = 35,
        ),
        // The three below are not essential, and that is a decision rather than an oversight.
        //
        // Claims and the walkthrough sit behind the paywall. If either counted towards a clean
        // verdict then a buyer on the free trial could never reach one, and the app would be using
        // the word "incomplete" to sell a subscription. The IMEI is free but needs the seller to read
        // a number out, which they may simply refuse to do — and that is the seller's behaviour, not
        // a fault in the phone, so it must not darken the verdict either.
        RunStep(
            id = "claims",
            title = "Claimed against measured",
            why = "Is it the phone the advert promised?",
            effort = StepEffort.ASK_THE_SELLER,
            typicalSeconds = 60,
            essential = false,
        ),
        RunStep(
            id = "imei",
            title = "IMEI and the stolen-phone register",
            why = "Check the number, then check it against the government CEIR portal",
            effort = StepEffort.ASK_THE_SELLER,
            typicalSeconds = 60,
            essential = false,
        ),
        RunStep(
            id = "guide",
            title = "What the app cannot test",
            why = "A twisted frame, a re-glued screen, the water sticker in the SIM slot",
            effort = StepEffort.LOOK_YOURSELF,
            typicalSeconds = 240,
            essential = false,
        ),
    )

    /** Ids in run order, for the app module's navigation assertion. */
    val stepIds: List<String> = steps.map { it.id }

    fun step(id: String): RunStep? = steps.firstOrNull { it.id == id }

    /** Total run length in whole minutes, rounded up, for "about 10 minutes" before it starts. */
    val typicalMinutes: Int = (steps.sumOf { it.typicalSeconds } + 59) / 60

    /** Length of just the steps that gate a clean verdict, for the buyer in a hurry. */
    val essentialMinutes: Int =
        (steps.filter { it.essential }.sumOf { it.typicalSeconds } + 59) / 60

    init {
        require(steps.map { it.id }.toSet().size == steps.size) {
            "Two run steps share an id, so one of them would be unreachable"
        }
        require(steps.any { it.essential }) {
            "No essential steps left, so every run would be judged complete without measuring " +
                "anything"
        }
    }
}
