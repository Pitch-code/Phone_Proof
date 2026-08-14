package com.phoneproof.feature.guide

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin

/**
 * The moving diagrams.
 *
 * Drawn with Canvas rather than shipped as GIFs or video, and the reasons are practical rather than
 * aesthetic. A set of eight animations as video would add tens of megabytes to a 13 MB app, on a
 * phone that may be someone's only device. Stock footage cannot be licensed for a paid app without
 * paying for it, and finding a clip of a fingernail running along a phone seam is not realistic.
 * Drawing them costs kilobytes, stays sharp on any screen, and can be adjusted when the wording
 * changes.
 *
 * Every diagram is a function of `progress` in 0f..1f rather than reading a clock. That makes each
 * one a pure drawing, so a screenshot test can capture any frame it likes and a reviewer can
 * actually see the middle of the motion. An animation that only exists as elapsed time cannot be
 * reviewed at all in this project, because there is no emulator to watch it on.
 */
@Composable
internal fun GuideDiagramCanvas(
    diagram: GuideDiagram,
    progress: Float,
    ink: Color,
    accent: Color,
    warn: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        when (diagram) {
            GuideDiagram.FRAME_TWIST -> drawFrameTwist(progress, ink, warn)
            GuideDiagram.SCREEN_SEAM -> drawScreenSeam(progress, ink, warn)
            GuideDiagram.WATER_STICKER -> drawWaterSticker(progress, ink, warn)
            GuideDiagram.SPEAKER_SEAL -> drawSpeakerSeal(progress, ink, accent)
            GuideDiagram.LENS_DUST -> drawLensDust(progress, ink, warn)
            GuideDiagram.FINGERPRINT -> drawFingerprint(progress, ink, accent)
            GuideDiagram.ACCOUNT_REMOVED -> drawAccountRemoved(progress, ink, warn)
            GuideDiagram.CHARGING_PORT -> drawChargingPort(progress, ink, accent)
        }
    }
}

/** A sine wave over the full cycle, so every diagram returns to where it started. */
private fun wave(progress: Float): Float = sin(progress * 2f * Math.PI.toFloat())

/**
 * An opaque fill for parts that must hide what is behind them.
 *
 * A translucent ink would let the sticker show through the closed SIM tray, which would destroy the
 * one thing that diagram is trying to say. Matched to the raised surface the diagram sits on.
 */
private val PhoneProofSurface = Color(0xFF18181B)

// --- The hand ---------------------------------------------------------------------------------

/**
 * A hand, because every step on this screen is something a person does with one.
 *
 * These diagrams previously drew the actor as whatever primitive was nearest to hand: the frame
 * twist used two grey bars, the screen seam a triangle, the fingerprint a single arc, and the other
 * five showed no person at all. On a real phone the bars read as pencils. A screen whose entire
 * subject is "what to do with your hands" was illustrating everything except the hands.
 *
 * One outline, two variants, eight callers. Drawn as a polygon rather than with curves: at the size
 * these appear in the frame grid a hand is barely thirty pixels tall, where a bezier buys nothing
 * that can be seen, and `lineTo` cannot behave differently between Compose versions.
 *
 * Positioned by **fingertip** rather than by centre, and rotated about it, because in every one of
 * these diagrams it is the fingertip that has to land on something exact — the seam, the sensor, the
 * row in a list. Anchoring at the centre would mean recomputing an offset at all eight call sites.
 *
 * Filled with translucent ink and stroked with opaque ink, so the hand never hides the fault it is
 * pointing at, and so it works in both themes without naming a colour of its own.
 *
 * `internal` rather than private for the same reason [GuideDiagramCanvas] is: eight diagrams share
 * this shape, the frame grid renders it barely thirty pixels tall, and a primitive that cannot be
 * looked at on its own is one nobody has reviewed. `GuideScreenshotTest` draws it large.
 */
internal fun DrawScope.drawHand(
    tip: Offset,
    length: Float,
    angleDegrees: Float,
    ink: Color,
    pointing: Boolean = true,
    alpha: Float = 1f,
) {
    val outline = if (pointing) PointingHand else Fist
    val creases = if (pointing) PointingHandCreases else FistCreases

    rotate(degrees = angleDegrees, pivot = tip) {
        val path = Path()
        outline.forEachIndexed { index, point ->
            val x = tip.x + point.x * length
            val y = tip.y + point.y * length
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        drawPath(path, ink.copy(alpha = 0.20f * alpha))
        // Thin, because the scallops between the fingers are only about a thirtieth of the hand
        // apart: a heavier stroke closes the gaps and hands the shape back to being an oval.
        drawPath(
            path,
            ink.copy(alpha = 0.85f * alpha),
            style = Stroke(width = length * 0.030f),
        )

        // The folded fingers. Without them the fist is a bean, and the bean is what the old two-bar
        // version already looked like.
        creases.forEach { (from, to) ->
            drawLine(
                color = ink.copy(alpha = 0.45f * alpha),
                start = Offset(tip.x + from.x * length, tip.y + from.y * length),
                end = Offset(tip.x + to.x * length, tip.y + to.y * length),
                strokeWidth = length * 0.020f,
            )
        }
    }
}

/**
 * The pointing hand, as fractions of its own length, fingertip at the origin and pointing up.
 *
 * Two things carry the reading, and both are in the **silhouette**:
 *
 *  - The folded fingers are **scallops down the right edge**, not lines drawn inside the shape. The
 *    first attempt used three parallel interior stripes; rendered, they read as grill marks and the
 *    whole hand as a leaf. At thirty pixels an interior line is texture, and texture is noise.
 *  - The thumb **leaves the outline** on the left, with a notch behind it. A thumb absorbed into a
 *    smooth curve is not a thumb, and without one the shape was a potato.
 *
 * Walked clockwise from the left edge of the index finger.
 */
private val PointingHand = listOf(
    // The index finger: long and narrow, because it is the part that has to look deliberate.
    Offset(-0.055f, 0.050f),
    Offset(-0.032f, 0.006f),
    Offset(0.032f, 0.006f),
    Offset(0.055f, 0.050f),
    Offset(0.055f, 0.330f),
    // Knuckles, then three folded fingers as bumps down the right edge.
    Offset(0.150f, 0.370f),
    Offset(0.205f, 0.445f),
    Offset(0.160f, 0.520f),
    Offset(0.235f, 0.585f),
    Offset(0.180f, 0.665f),
    Offset(0.240f, 0.725f),
    Offset(0.185f, 0.805f),
    Offset(0.215f, 0.865f),
    Offset(0.140f, 0.945f),
    // Wrist.
    Offset(0.020f, 1.000f),
    Offset(-0.140f, 0.970f),
    // The thumb, out to the left with a notch behind it.
    Offset(-0.225f, 0.885f),
    Offset(-0.345f, 0.780f),
    Offset(-0.375f, 0.680f),
    Offset(-0.300f, 0.605f),
    Offset(-0.205f, 0.585f),
    // Back up the palm to the base of the index finger.
    Offset(-0.160f, 0.460f),
    Offset(-0.100f, 0.380f),
)

/**
 * The same hand closed, for the steps where something is held rather than touched.
 *
 * The knuckles are scalloped along the **leading** edge — the end nearest the fingertip anchor — so
 * that the four folded fingers are what meets whatever is being gripped. Rebuilt for the same reason
 * as the pointing hand: as a smooth oval with stripes across it, this was a potato.
 */
private val Fist = listOf(
    // Four knuckles across the gripping edge.
    Offset(-0.170f, 0.175f),
    Offset(-0.105f, 0.060f),
    Offset(-0.020f, 0.120f),
    Offset(0.050f, 0.030f),
    Offset(0.130f, 0.100f),
    Offset(0.200f, 0.045f),
    Offset(0.265f, 0.140f),
    Offset(0.310f, 0.105f),
    Offset(0.340f, 0.225f),
    // Down the far side and round the wrist.
    Offset(0.350f, 0.440f),
    Offset(0.315f, 0.680f),
    Offset(0.255f, 0.860f),
    Offset(0.120f, 0.990f),
    Offset(-0.060f, 1.000f),
    Offset(-0.200f, 0.920f),
    // The thumb, crossing the front of the fist.
    Offset(-0.300f, 0.800f),
    Offset(-0.400f, 0.660f),
    Offset(-0.380f, 0.540f),
    Offset(-0.285f, 0.500f),
    Offset(-0.240f, 0.345f),
)

/**
 * Where one finger ends and the next begins.
 *
 * Drawn from the valley between two scallops **into** the hand, across the direction of the fingers
 * rather than along it. The stripes this replaced ran the other way, which is why they read as a
 * grille: a line parallel to the knuckle edge describes a surface, a line perpendicular to it
 * describes a gap.
 */
private val PointingHandCreases = listOf(
    Offset(0.160f, 0.520f) to Offset(0.055f, 0.505f),
    Offset(0.180f, 0.665f) to Offset(0.070f, 0.650f),
    Offset(0.185f, 0.805f) to Offset(0.085f, 0.790f),
)

private val FistCreases = listOf(
    Offset(-0.020f, 0.120f) to Offset(-0.030f, 0.330f),
    Offset(0.130f, 0.100f) to Offset(0.120f, 0.320f),
    Offset(0.265f, 0.140f) to Offset(0.250f, 0.330f),
)

// ---------------------------------------------------------------------------------------------

/** A phone outline twisting, with the flex exaggerated so the motion reads at thumbnail size. */
private fun DrawScope.drawFrameTwist(progress: Float, ink: Color, warn: Color) {
    val twist = wave(progress)
    val w = size.width
    val h = size.height
    val bodyW = w * 0.34f
    val bodyH = h * 0.66f
    val cx = w / 2f
    val cy = h / 2f
    val lean = twist * h * 0.05f

    // Drawn as a path rather than a rotated rectangle: the two ends move in opposite directions,
    // which is what twisting looks like and what a rotation cannot show.
    val path = Path().apply {
        moveTo(cx - bodyW / 2f, cy - bodyH / 2f + lean)
        lineTo(cx + bodyW / 2f, cy - bodyH / 2f - lean)
        lineTo(cx + bodyW / 2f, cy + bodyH / 2f + lean)
        lineTo(cx - bodyW / 2f, cy + bodyH / 2f - lean)
        close()
    }
    drawPath(path, ink, style = Stroke(width = h * 0.018f))

    // A hand at each end, as the step says: "one hand at each end", wringing in opposite directions.
    //
    // Each fist is tilted with the end it is holding, so the two hands counter-rotate. That is the
    // whole gesture, and it is what two straight bars could never show: bars leaning in opposite
    // directions read as a diagram of parallax, not as a pair of wrists.
    drawHand(
        tip = Offset(cx, cy - bodyH / 2f + h * 0.03f + lean),
        length = h * 0.26f,
        angleDegrees = 180f + twist * 12f,
        ink = ink,
        pointing = false,
    )
    drawHand(
        tip = Offset(cx, cy + bodyH / 2f - h * 0.03f - lean),
        length = h * 0.26f,
        angleDegrees = -twist * 12f,
        ink = ink,
        pointing = false,
    )

    // A creak mark appears at the extremes of the twist, where a bent frame would complain.
    if (kotlin.math.abs(twist) > 0.75f) {
        val markY = cy - bodyH * 0.18f
        listOf(-1f, 1f).forEach { side ->
            drawLine(
                color = warn,
                start = Offset(cx + side * bodyW * 0.62f, markY),
                end = Offset(cx + side * bodyW * 0.85f, markY - h * 0.035f),
                strokeWidth = h * 0.014f,
            )
        }
    }
}

/** A cross-section of the glass and frame, with a gap opening at one corner. */
private fun DrawScope.drawScreenSeam(progress: Float, ink: Color, warn: Color) {
    val w = size.width
    val h = size.height
    val lift = ((wave(progress) + 1f) / 2f) * h * 0.10f

    // Frame: a flat bar across the lower half.
    drawRect(
        color = ink.copy(alpha = 0.35f),
        topLeft = Offset(w * 0.12f, h * 0.58f),
        size = Size(w * 0.76f, h * 0.16f),
    )

    // Glass: a bar that lifts at its right end.
    val glass = Path().apply {
        moveTo(w * 0.12f, h * 0.50f)
        lineTo(w * 0.62f, h * 0.50f)
        lineTo(w * 0.88f, h * 0.50f - lift)
        lineTo(w * 0.88f, h * 0.44f - lift)
        lineTo(w * 0.62f, h * 0.44f)
        lineTo(w * 0.12f, h * 0.44f)
        close()
    }
    drawPath(glass, ink, style = Stroke(width = h * 0.016f))

    // The gap itself, marked in the warning colour once it is wide enough to catch a nail.
    if (lift > h * 0.03f) {
        drawLine(
            color = warn,
            start = Offset(w * 0.86f, h * 0.50f - lift),
            end = Offset(w * 0.86f, h * 0.58f),
            strokeWidth = h * 0.014f,
        )
    }

    // A fingertip travelling along the seam, which is the action being described. Was a triangle,
    // which said "nail" to whoever wrote it and "grey wedge" to everyone else.
    //
    // Tilted left so the hand sits back over the flat part of the glass rather than over the lifting
    // end: the gap on the right is the fault, and the actor must not stand in front of it.
    val nailX = w * 0.18f + ((wave(progress) + 1f) / 2f) * w * 0.62f
    drawHand(
        tip = Offset(nailX, h * 0.42f),
        length = h * 0.40f,
        angleDegrees = 152f,
        ink = ink,
    )
}

/** A SIM tray sliding out, revealing a sticker that turns from white to red. */
private fun DrawScope.drawWaterSticker(progress: Float, ink: Color, warn: Color) {
    val w = size.width
    val h = size.height
    val out = ((wave(progress) + 1f) / 2f)

    // Rebuilt after the frame grid showed the first attempt was nonsense: the sticker was drawn
    // behind the tray and the tray floated away from the phone, so it read as a grey box, an orange
    // dot and an unrelated rectangle. The body now ends where the slot begins, and the tray slides
    // out of that opening.
    val bodyLeft = w * 0.06f
    val bodyRight = w * 0.52f
    val slotTop = h * 0.42f
    val slotHeight = h * 0.16f

    drawRect(
        color = ink.copy(alpha = 0.30f),
        topLeft = Offset(bodyLeft, h * 0.28f),
        size = Size(bodyRight - bodyLeft, h * 0.44f),
    )

    // The opening, cut into the right edge of the body.
    drawRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(bodyRight - w * 0.10f, slotTop),
        size = Size(w * 0.10f, slotHeight),
    )

    // The sticker, deep inside the slot. Drawn before the tray so the tray covers it while closed,
    // which is the point of the step: it cannot be seen until the tray is out.
    drawCircle(
        color = warn,
        radius = h * 0.045f,
        center = Offset(bodyRight - w * 0.055f, slotTop + slotHeight / 2f),
    )

    // The tray, sliding out of the opening and staying attached to it.
    // Short travel on purpose. A longer slide left the tray floating in space with a gap between it
    // and the phone, which read as two unrelated objects rather than one being pulled from the other.
    // Travel halved again. At the old distance the tray's near edge cleared the phone entirely and
    // left a visible gap, so the drawing became a box, a dot and an unrelated rectangle — the exact
    // fault the rebuild above was meant to have fixed. Adding a hand pulling from the right made it
    // worse, because the eye now had somewhere else to go. It now stops flush with the body.
    val trayX = bodyRight - w * 0.10f + out * w * 0.10f
    drawRect(
        color = PhoneProofSurface,
        topLeft = Offset(trayX, slotTop),
        size = Size(w * 0.24f, slotHeight),
    )
    drawRect(
        color = ink,
        topLeft = Offset(trayX, slotTop),
        size = Size(w * 0.24f, slotHeight),
        style = Stroke(width = h * 0.012f),
    )
    // A pin hole on the tray face, so it is recognisable as a SIM tray rather than a plain box.
    drawCircle(
        color = ink.copy(alpha = 0.7f),
        radius = h * 0.012f,
        center = Offset(trayX + w * 0.03f, slotTop + slotHeight / 2f),
        style = Stroke(width = h * 0.008f),
    )

    // The hand that pulled it, reaching in from the right and travelling with the tray. Without it
    // the tray moves by itself, which is the one thing a SIM tray never does.
    drawHand(
        tip = Offset(trayX + w * 0.21f, slotTop + slotHeight / 2f),
        length = h * 0.27f,
        angleDegrees = 270f,
        ink = ink,
    )
}

/** A grille with air being drawn through it, which is what a broken seal allows. */
private fun DrawScope.drawSpeakerSeal(progress: Float, ink: Color, accent: Color) {
    val w = size.width
    val h = size.height

    // The bottom edge of the phone, so the grille sits on something and the hand has something to
    // hold. Without it this was the least physical of the eight: a row of bars, an arc, and no object.
    drawRect(
        color = ink.copy(alpha = 0.14f),
        topLeft = Offset(w * 0.24f, h * 0.46f),
        size = Size(w * 0.54f, h * 0.21f),
    )

    // The grille: a row of slots.
    val slots = 7
    val slotW = w * 0.035f
    val startX = w * 0.5f - (slots * slotW * 1.8f) / 2f
    repeat(slots) { i ->
        drawRect(
            color = ink.copy(alpha = 0.55f),
            topLeft = Offset(startX + i * slotW * 1.8f, h * 0.52f),
            size = Size(slotW, h * 0.09f),
        )
    }

    // Air arrows rising through the grille. Three, staggered, so the flow reads as continuous.
    repeat(3) { i ->
        val phase = ((progress + i / 3f) % 1f)
        val y = h * 0.52f - phase * h * 0.30f
        val alpha = (1f - phase) * 0.9f
        val x = w * 0.5f + (i - 1) * w * 0.13f
        drawLine(
            color = accent.copy(alpha = alpha),
            start = Offset(x, y + h * 0.06f),
            end = Offset(x, y),
            strokeWidth = h * 0.013f,
        )
        drawLine(
            color = accent.copy(alpha = alpha),
            start = Offset(x - w * 0.02f, y + h * 0.025f),
            end = Offset(x, y),
            strokeWidth = h * 0.013f,
        )
        drawLine(
            color = accent.copy(alpha = alpha),
            start = Offset(x + w * 0.02f, y + h * 0.025f),
            end = Offset(x, y),
            strokeWidth = h * 0.013f,
        )
    }

    // Lips, as a simple arc below the grille.
    drawArc(
        color = ink.copy(alpha = 0.45f),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.34f, h * 0.66f),
        size = Size(w * 0.32f, h * 0.22f),
        style = Stroke(width = h * 0.018f),
    )

    // The hand holding the phone up to the mouth.
    //
    // The one diagram where the hand is not the actor — the mouth is — so it holds rather than does.
    // It needs something to hold, though: on its own at the edge of the frame it was a hand floating
    // beside a row of bars. The phone edge below carries the grille and gives the grip a subject.
    drawHand(
        tip = Offset(w * 0.255f, h * 0.565f),
        length = h * 0.30f,
        angleDegrees = 90f,
        ink = ink,
        pointing = false,
        alpha = 0.8f,
    )
}

/** A lens with a light sweeping across it, picking specks out of the glass. */
private fun DrawScope.drawLensDust(progress: Float, ink: Color, warn: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = h * 0.26f

    drawCircle(color = ink.copy(alpha = 0.28f), radius = r, center = Offset(cx, cy))
    drawCircle(color = ink, radius = r, center = Offset(cx, cy), style = Stroke(width = h * 0.016f))
    drawCircle(
        color = ink.copy(alpha = 0.5f),
        radius = r * 0.55f,
        center = Offset(cx, cy),
        style = Stroke(width = h * 0.010f),
    )

    // The torch beam, sweeping side to side. Held to one side, as the instructions say, not straight on.
    val beamX = cx + wave(progress) * w * 0.30f
    val beam = Path().apply {
        moveTo(beamX, h * 0.06f)
        lineTo(beamX + w * 0.10f, h * 0.06f)
        lineTo(cx + w * 0.05f, cy)
        lineTo(cx - w * 0.05f, cy)
        close()
    }
    drawPath(beam, Color.White.copy(alpha = 0.10f))

    // Specks, lit only while the beam is near them, which is exactly why the step says to use a
    // light from an angle: the dust is invisible face-on.
    val specks = listOf(
        Offset(cx - r * 0.35f, cy - r * 0.25f),
        Offset(cx + r * 0.20f, cy + r * 0.40f),
        Offset(cx + r * 0.45f, cy - r * 0.35f),
    )
    specks.forEach { speck ->
        val distance = kotlin.math.abs(speck.x - beamX) / (w * 0.5f)
        val lit = (1f - distance).coerceIn(0f, 1f)
        drawCircle(
            color = warn.copy(alpha = lit),
            radius = h * 0.014f,
            center = speck,
        )
    }

    // The hand holding the torch, moving with the beam so the sweep has a cause.
    //
    // It enters from the top edge and is deliberately allowed to run off it. A hand cropped by the
    // frame reads as a hand coming into shot; a whole hand shrunk to fit would be a mitten floating
    // above a lens, and the beam is what matters here.
    // The torch itself. Without it the hand held nothing and the beam had no source, so the drawing
    // was a hand, a circle and a grey wedge between them.
    drawRect(
        color = ink.copy(alpha = 0.30f),
        topLeft = Offset(beamX + w * 0.005f, h * 0.055f),
        size = Size(w * 0.075f, h * 0.155f),
    )
    drawRect(
        color = ink,
        topLeft = Offset(beamX + w * 0.005f, h * 0.055f),
        size = Size(w * 0.075f, h * 0.155f),
        style = Stroke(width = h * 0.012f),
    )

    // The hand wrapped round it, low enough that the barrel shows above the knuckles.
    drawHand(
        tip = Offset(beamX + w * 0.042f, h * 0.20f),
        length = h * 0.26f,
        angleDegrees = 174f,
        ink = ink,
        pointing = false,
    )
}

/** A fingertip on a sensor, with a ripple confirming a read. */
private fun DrawScope.drawFingerprint(progress: Float, ink: Color, accent: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.55f

    // The sensor area, and the arches of a print.
    repeat(4) { i ->
        val r = h * (0.08f + i * 0.045f)
        drawArc(
            color = ink.copy(alpha = 0.65f - i * 0.10f),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = h * 0.012f),
        )
    }

    // The ripple: expands and fades once per cycle, standing in for a successful read.
    val ripple = progress
    drawCircle(
        color = accent.copy(alpha = (1f - ripple) * 0.8f),
        radius = h * (0.10f + ripple * 0.22f),
        center = Offset(cx, cy),
        style = Stroke(width = h * 0.012f),
    )

    // A finger descending onto the sensor. Was a lone arc, which at this size was a croissant.
    //
    // `press` moves the whole hand rather than only the tip, so the finger arrives at the sensor
    // instead of stretching towards it.
    val press = (1f - kotlin.math.abs(wave(progress))) * h * 0.05f
    // Comes in steeply from the upper right rather than straight down.
    //
    // Straight down put the whole hand on top of the print, and the print is the subject: the render
    // showed a hand and a ripple with the arches hidden behind them. Approaching at an angle lands
    // the fingertip on the sensor while leaving the arches to the left of it visible.
    drawHand(
        tip = Offset(cx, cy - h * 0.05f - press),
        length = h * 0.36f,
        angleDegrees = 132f,
        ink = ink,
    )
}

/** An account row being struck through and removed from a list. */
private fun DrawScope.drawAccountRemoved(progress: Float, ink: Color, warn: Color) {
    val w = size.width
    val h = size.height

    // A settings list: three rows.
    repeat(3) { i ->
        val y = h * (0.28f + i * 0.20f)
        drawRect(
            color = ink.copy(alpha = 0.16f),
            topLeft = Offset(w * 0.16f, y),
            size = Size(w * 0.68f, h * 0.13f),
        )
        drawCircle(
            color = ink.copy(alpha = 0.45f),
            radius = h * 0.035f,
            center = Offset(w * 0.24f, y + h * 0.065f),
        )
        drawLine(
            color = ink.copy(alpha = 0.45f),
            start = Offset(w * 0.32f, y + h * 0.065f),
            end = Offset(w * 0.70f, y + h * 0.065f),
            strokeWidth = h * 0.014f,
        )
    }

    // The middle row is the seller's account being removed.
    //
    // Rebuilt after the frame grid: a horizontal bar growing left to right read as a progress bar
    // filling up, which is the opposite of the meaning. A diagonal cross means cancel in a way a
    // horizontal line never will, and the row fades as it goes so the end state is an empty slot
    // rather than a marked-up one.
    val y = h * 0.48f
    val strike = (progress * 1.5f).coerceIn(0f, 1f)
    val fade = 1f - strike

    drawRect(
        color = PhoneProofSurface.copy(alpha = strike * 0.9f),
        topLeft = Offset(w * 0.16f, y),
        size = Size(w * 0.68f, h * 0.13f),
    )
    drawCircle(
        color = warn.copy(alpha = fade),
        radius = h * 0.035f,
        center = Offset(w * 0.24f, y + h * 0.065f),
    )

    val cx = w * 0.24f
    val cy = y + h * 0.065f
    val arm = h * 0.030f * strike
    drawLine(
        color = warn,
        start = Offset(cx - arm, cy - arm),
        end = Offset(cx + arm, cy + arm),
        strokeWidth = h * 0.014f,
    )
    drawLine(
        color = warn,
        start = Offset(cx + arm, cy - arm),
        end = Offset(cx - arm, cy + arm),
        strokeWidth = h * 0.014f,
    )

    // The finger doing the removing, tapping the row that is going.
    //
    // Placed to the right of the crossed-out avatar rather than on it: the cross is the outcome and
    // must stay visible. It descends as the strike completes, so the tap and the result are one
    // motion rather than two things that happen to be on screen together.
    // Shorter and further right than first drawn, and allowed to run off the frame.
    //
    // At full length the hand reached back across the row above and looked as though it were tapping
    // that one instead. Three rows twenty percent of the height apart leave no room for a whole hand
    // between them, so it leaves by the edge rather than shrinking to fit.
    drawHand(
        tip = Offset(w * 0.72f, y + h * 0.055f - (1f - strike) * h * 0.09f),
        length = h * 0.25f,
        angleDegrees = 208f,
        ink = ink,
    )
}

/** A cable in a port, wiggling, with the charge indicator dropping out when it moves too far. */
private fun DrawScope.drawChargingPort(progress: Float, ink: Color, accent: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val tilt = wave(progress) * 9f

    // The bottom edge of the phone.
    drawRect(
        color = ink.copy(alpha = 0.30f),
        topLeft = Offset(w * 0.18f, h * 0.20f),
        size = Size(w * 0.64f, h * 0.30f),
    )
    // The port mouth.
    drawRect(
        color = ink.copy(alpha = 0.65f),
        topLeft = Offset(cx - w * 0.07f, h * 0.46f),
        size = Size(w * 0.14f, h * 0.05f),
    )

    // The connector and lead, rotated about the port so the movement is a wiggle rather than a slide.
    rotate(degrees = tilt, pivot = Offset(cx, h * 0.48f)) {
        drawRect(
            color = ink,
            topLeft = Offset(cx - w * 0.055f, h * 0.48f),
            size = Size(w * 0.11f, h * 0.12f),
            style = Stroke(width = h * 0.014f),
        )
        drawLine(
            color = ink,
            start = Offset(cx, h * 0.60f),
            end = Offset(cx, h * 0.86f),
            strokeWidth = h * 0.022f,
        )

        // The hand doing the wiggling, inside the rotate so it travels with the lead it is holding.
        // Outside it, the cable would swing while the hand stayed still.
        drawHand(
            tip = Offset(cx, h * 0.64f),
            length = h * 0.30f,
            angleDegrees = 0f,
            ink = ink,
            pointing = false,
        )
    }

    // The charging light, which cuts out at the extremes of the wiggle — the fault being looked for.
    val connected = kotlin.math.abs(wave(progress)) < 0.8f
    drawCircle(
        color = if (connected) accent else accent.copy(alpha = 0.12f),
        radius = h * 0.03f,
        center = Offset(w * 0.72f, h * 0.33f),
    )
}
