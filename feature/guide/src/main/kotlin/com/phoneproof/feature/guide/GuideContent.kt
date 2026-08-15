package com.phoneproof.feature.guide

import androidx.compose.runtime.Immutable

/**
 * Which diagram illustrates a step, and the single frame of it that is drawn.
 *
 * The diagrams used to loop. They no longer do — `Motion.kt` states that an infinite animation is a
 * bug in this codebase, because a perpetually animating surface is an uncontrolled load and the
 * battery check cannot take an honest reading next to one. These eight were a second, undocumented
 * exception to that rule, and the product owner has ruled that they hold still.
 *
 * Which makes [stillFrame] load-bearing rather than a default. Each drawing is a function of progress
 * over one cycle, and most of them are **at rest at 0** — the frame is untwisted, the SIM tray is
 * closed, the finger has not touched the sensor. A single shared constant was fine when it was only
 * the fallback for "animations are switched off"; now that it is the *only* thing anyone sees, each
 * diagram needs the moment that actually shows its action, and that moment is not in the same place
 * for all eight.
 */
enum class GuideDiagram(val stillFrame: Float) {
    /** Fully twisted, where the creak marks appear. */
    FRAME_TWIST(0.25f),

    /**
     * Mid-seam, not at the far end.
     *
     * The gap and the fingertip are driven by the same wave, so at full lift the finger is standing
     * directly on the gap it is meant to be revealing. Half way, both are visible.
     */
    SCREEN_SEAM(0.5f),

    /** Tray fully out, which is the only frame where the sticker can be seen at all. */
    WATER_STICKER(0.25f),

    /** Arrows spread across the grille rather than bunched. */
    SPEAKER_SEAL(0.25f),

    /** Beam centred, which is the one position that lights all three specks. */
    LENS_DUST(0.5f),

    /** Finger fully pressed. At 0.25 it is at the top of its travel, hovering over the sensor. */
    FINGERPRINT(0.5f),

    /** Fully struck through. The strike is the outcome, and anything earlier is a half-done job. */
    ACCOUNT_REMOVED(0.75f),

    /** Tilted far enough that the charge light drops out — the fault being looked for. */
    CHARGING_PORT(0.25f),
}

/**
 * One thing to check by hand.
 *
 * Split into why, how, and what each result means, because a buyer reading this is standing in
 * front of a seller with about two minutes. "Why" comes first: someone who does not understand
 * why a check matters will skip it, and the ones people skip are the expensive ones.
 *
 * Written in short, plain sentences on purpose. The reader may not be a native English speaker,
 * may be reading on a cracked screen in bad light, and is being watched while they read.
 */
@Immutable
data class GuideStep(
    val id: String,
    val title: String,
    /** One line, on the card before it is opened. */
    val summary: String,
    val whyItMatters: String,
    /** Numbered in the UI. Each one an action, not an explanation. */
    val howTo: List<String>,
    val goodSign: String,
    val badSign: String,
    val diagram: GuideDiagram,
)

/**
 * The checks an app cannot do.
 *
 * Everything here needs hands, eyes or a torch. None of it can be measured in software, which is
 * exactly why it belongs in one place the buyer can work through — the faults listed here are the
 * ones that cost the most and are hidden the most easily.
 */
val GuideSteps: List<GuideStep> = listOf(
    GuideStep(
        id = "guide.frame",
        title = "Twist the phone gently",
        summary = "Find out if it has been dropped hard or opened up",
        whyItMatters = "A phone that has been dropped hard, or opened for a repair, is never quite " +
            "square again. The frame flexes and the glue no longer holds evenly. This is the " +
            "fastest way to tell that a phone has had a rough life, and it takes five seconds.",
        howTo = listOf(
            "Hold the phone flat in both hands, one hand at each end.",
            "Twist very gently, as if wringing out a small towel. Do not force it.",
            "Listen closely, and watch the screen edges.",
            "Now press the middle of the back with your thumb.",
        ),
        goodSign = "Silent and solid. Nothing moves, creaks or clicks.",
        badSign = "A creak, a click, or a soft crunch. Any flex you can feel. These mean the " +
            "frame is bent or the phone has been opened before.",
        diagram = GuideDiagram.FRAME_TWIST,
    ),
    GuideStep(
        id = "guide.seam",
        title = "Run a fingernail around the screen edge",
        summary = "Check whether the screen has been off and re-glued",
        whyItMatters = "When a screen is replaced, it is almost never sealed as well as the " +
            "factory sealed it. A lifted edge lets in dust and water, and it tells you the phone " +
            "has been repaired even if the seller says it has not. A replaced screen is also often " +
            "a cheap copy, which is dimmer and less accurate than the original.",
        howTo = listOf(
            "Run a fingernail slowly all the way around where the glass meets the frame.",
            "Pay special attention to the corners. That is where a re-glued screen lifts first.",
            "Hold the phone side-on against a light and look along the seam.",
            "Look for glue squeezed out, or a thin dark line under the glass.",
        ),
        goodSign = "The seam feels like one smooth piece. Your nail does not catch anywhere.",
        badSign = "Your nail catches or drops into a gap. You can see glue, or a gap that lets " +
            "light through.",
        diagram = GuideDiagram.SCREEN_SEAM,
    ),
    GuideStep(
        id = "guide.water",
        title = "Look at the water sticker in the SIM slot",
        summary = "The one honest record of water damage",
        whyItMatters = "Almost every phone has a small sticker inside the SIM tray slot that " +
            "changes colour if water gets in. It cannot be reset, and most sellers do not know it " +
            "is there. Water damage does not always kill a phone straight away — it corrodes it " +
            "over weeks, so a phone that works today can fail a month after you pay.",
        howTo = listOf(
            "Ask the seller for the SIM ejector pin, or use an earring.",
            "Push it into the small hole and pull the tray out.",
            "Shine your phone's torch into the empty slot.",
            "Find the small sticker. Note its colour.",
        ),
        goodSign = "The sticker is white, or white with red lines.",
        badSign = "The sticker is solid red, pink or orange. That means water has been inside. " +
            "No sticker at all is also a warning: it usually means the phone has been opened.",
        diagram = GuideDiagram.WATER_STICKER,
    ),
    GuideStep(
        id = "guide.seal",
        title = "Suck gently on the speaker grille",
        summary = "Test whether the waterproof seals are still there",
        whyItMatters = "A sealed phone resists air the same way it resists water. If you can pull " +
            "air straight through it, the seals are gone — usually because it has been opened. The " +
            "phone will still work, but it is no longer water resistant, whatever the box says.",
        howTo = listOf(
            "Put your lips around the earpiece grille at the top of the screen.",
            "Suck gently. Do not blow, and do not suck hard.",
            "Feel whether air moves through easily.",
            "Try the same on the bottom speaker.",
        ),
        goodSign = "Almost no air moves. It feels blocked, like sucking on a sealed straw.",
        badSign = "Air flows through freely and easily. The seals or the mesh are missing.",
        diagram = GuideDiagram.SPEAKER_SEAL,
    ),
    GuideStep(
        id = "guide.lens",
        title = "Shine a light across every camera lens",
        summary = "Find dust and scratches inside the glass",
        whyItMatters = "Dust inside a camera lens softens every photo you will ever take, and it " +
            "cannot be cleaned without opening the phone. It is invisible when you look straight " +
            "at the lens and obvious from an angle. Sellers photograph phones from the front, so " +
            "this is easy to miss.",
        howTo = listOf(
            "Use another phone's torch, held to one side rather than straight on.",
            "Look into each rear lens in turn, from a low angle.",
            "Do the same for the front camera.",
            "Then open the camera app, cover nothing, and look for a hazy patch in the picture.",
        ),
        goodSign = "The glass is clear. Reflections are sharp and even.",
        badSign = "Specks or fibres inside the glass. Fine scratch marks in a circle, which mean " +
            "someone has polished the lens. A milky haze in photos.",
        diagram = GuideDiagram.LENS_DUST,
    ),
    GuideStep(
        id = "guide.biometrics",
        title = "Add your own fingerprint and face",
        summary = "The only way to know the sensors really work",
        whyItMatters = "A seller can show you their own fingerprint unlocking instantly and it " +
            "proves very little. Enrolling a new one exercises the whole sensor. Under-display " +
            "fingerprint readers fail after a screen replacement more often than any other part, " +
            "and it is the single most common fault on a repaired phone.",
        howTo = listOf(
            "Open Settings, then Security, then Fingerprint.",
            "Add a new fingerprint. Use a finger, not a thumb.",
            "Lock the phone and unlock it five times in a row.",
            "Do the same for face unlock if the phone has it.",
        ),
        goodSign = "It enrols in one go, and unlocks every time, quickly.",
        badSign = "It asks you to press again and again while enrolling. It fails on some parts " +
            "of the sensor. It is noticeably slow. Any of these mean the reader is failing.",
        diagram = GuideDiagram.FINGERPRINT,
    ),
    GuideStep(
        id = "guide.account",
        title = "Make the seller remove their account",
        summary = "Do this before you hand over money, not after",
        whyItMatters = "If the seller's Google account is still on the phone, it can be locked " +
            "remotely and factory resetting will not free it. The phone becomes a paperweight " +
            "that only they can unlock. This is the single most expensive mistake a used-phone " +
            "buyer can make, and once they have your money they have no reason to help.",
        howTo = listOf(
            "Open Settings, then Accounts. Check the list is empty of their accounts.",
            "If an account is there, ask them to remove it while you watch.",
            "Then factory reset the phone in front of them.",
            "Set the phone up as new. If it asks for their password, stop.",
        ),
        goodSign = "The phone sets up fresh and asks for your account, not theirs.",
        badSign = "A screen asking for the previous owner's password after the reset. Walk away " +
            "from this phone. Nobody can remove that lock but them.",
        diagram = GuideDiagram.ACCOUNT_REMOVED,
    ),
    GuideStep(
        id = "guide.port",
        title = "Wiggle a cable in the charging port",
        summary = "Check the port is not worn out or loose",
        whyItMatters = "The charging port takes more physical wear than any other part of a " +
            "phone. A loose one means charging that stops when you move, and on many phones the " +
            "port is soldered to the mainboard, which turns a cheap repair into an expensive one.",
        howTo = listOf(
            "Plug in a charging cable.",
            "Check the phone shows that it is charging.",
            "Wiggle the cable gently up and down, then side to side.",
            "Look inside the port with a torch for lint, or bent metal.",
        ),
        goodSign = "The cable clicks in firmly and charging never stops while you move it.",
        badSign = "Charging cuts in and out. The cable feels loose or falls out. The port is " +
            "packed with lint, or you can see damaged pins.",
        diagram = GuideDiagram.CHARGING_PORT,
    ),
)
