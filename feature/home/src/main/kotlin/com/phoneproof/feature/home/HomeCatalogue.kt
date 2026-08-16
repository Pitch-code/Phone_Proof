package com.phoneproof.feature.home

/**
 * One entry on Home's list of checks: what it is called, and where tapping it goes.
 *
 * [route] is the navigation route of the screen, the same arrangement the guided run uses for its
 * steps. `feature:home` has no business knowing how navigation works, so it holds the route as an
 * opaque string and the app module asserts that every one of them resolves.
 */
data class HomeCheckEntry(
    val route: String,
    val title: String,
    val subtitle: String,
)

/**
 * Every check Home offers, in the order it offers them, in one place.
 *
 * This list used to exist twice: once in the navigation graph, which is what the buyer sees, and once
 * inside `HomeScreenshotTest` under the name `realChecks()`. The comment above that copy read "every
 * check the app actually offers, not a token two" — and by the time the sensor test was added it was
 * five entries behind, silently missing the microphone, the cameras and the IMEI. So four PRs' worth of
 * Home renders were reviewed against a screen that had not existed for weeks, which is a worse failure
 * than having no render at all: it looks like evidence.
 *
 * Now there is one list. The navigation graph turns each entry into a tappable row, the screenshot test
 * renders the same entries, and neither can drift from the other.
 */
val HomeCatalogue: List<HomeCheckEntry> = listOf(
    HomeCheckEntry(
        route = "scan",
        title = "Instant scan",
        // "sensors" removed: the scan only reads the parts list, and now that a test actually
        // exercises them, a subtitle claiming both would be taking credit for the other screen's work.
        subtitle = "Software, storage, battery and screen — no waiting",
    ),
    HomeCheckEntry(
        route = "lock",
        title = "Remote lock control",
        subtitle = "Can a lender brick this phone after you pay?",
    ),
    HomeCheckEntry(
        route = "touch",
        title = "Touch response",
        subtitle = "Find dead patches on the screen",
    ),
    HomeCheckEntry(
        route = "multi-touch",
        title = "Fingers at once",
        subtitle = "How many fingers the screen can really follow",
    ),
    HomeCheckEntry(
        route = "screen-patterns",
        title = "Dead pixels and burn-in",
        subtitle = "Plain colours that make screen faults obvious",
    ),
    HomeCheckEntry(
        route = "audio",
        title = "Microphone, earpiece and speaker",
        subtitle = "Three separate parts, measured with a test tone",
    ),
    HomeCheckEntry(
        route = "camera",
        title = "Cameras and flashlight",
        subtitle = "Is each sensor producing a live picture?",
    ),
    HomeCheckEntry(
        route = "volume-buttons",
        title = "Volume buttons",
        subtitle = "Dead keys, and jammed ones that boot the phone into recovery",
    ),
    HomeCheckEntry(
        route = "vibration",
        title = "Vibration",
        subtitle = "Felt with the accelerometer, not guessed at",
    ),
    HomeCheckEntry(
        route = "sensors",
        title = "Sensors that still work",
        subtitle = "Tilt and cover it — a dead sensor is still on the parts list",
    ),
    HomeCheckEntry(
        route = "claims",
        title = "Claimed against measured",
        subtitle = "Is it the phone you were promised?",
    ),
    // Listed with the checks even though the buyer types the number, because from their side it is the
    // same kind of task: find out something about this handset before paying. The screen itself is
    // candid that Android will not supply it.
    HomeCheckEntry(
        route = "imei",
        title = "IMEI and the stolen-phone register",
        subtitle = "Check the number, then check it against CEIR",
    ),
)
