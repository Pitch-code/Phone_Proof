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

    // Hands, as two short bars at the ends.
    val handW = bodyW * 1.5f
    drawLine(
        color = ink.copy(alpha = 0.5f),
        start = Offset(cx - handW / 2f, cy - bodyH / 2f - h * 0.06f + lean),
        end = Offset(cx + handW / 2f, cy - bodyH / 2f - h * 0.06f - lean),
        strokeWidth = h * 0.03f,
    )
    drawLine(
        color = ink.copy(alpha = 0.5f),
        start = Offset(cx - handW / 2f, cy + bodyH / 2f + h * 0.06f - lean),
        end = Offset(cx + handW / 2f, cy + bodyH / 2f + h * 0.06f + lean),
        strokeWidth = h * 0.03f,
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

    // A fingernail travelling along the seam, which is the action being described.
    val nailX = w * 0.18f + ((wave(progress) + 1f) / 2f) * w * 0.62f
    val nail = Path().apply {
        moveTo(nailX, h * 0.30f)
        lineTo(nailX + w * 0.05f, h * 0.30f)
        lineTo(nailX + w * 0.025f, h * 0.41f)
        close()
    }
    drawPath(nail, ink.copy(alpha = 0.75f))
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
    val trayX = bodyRight - w * 0.10f + out * w * 0.20f
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
}

/** A grille with air being drawn through it, which is what a broken seal allows. */
private fun DrawScope.drawSpeakerSeal(progress: Float, ink: Color, accent: Color) {
    val w = size.width
    val h = size.height

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

    // A fingertip descending onto the sensor.
    val press = (1f - kotlin.math.abs(wave(progress))) * h * 0.05f
    drawArc(
        color = ink,
        startAngle = 0f,
        sweepAngle = -180f,
        useCenter = false,
        topLeft = Offset(cx - w * 0.10f, cy - h * 0.20f - press),
        size = Size(w * 0.20f, h * 0.20f),
        style = Stroke(width = h * 0.018f),
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
    }

    // The charging light, which cuts out at the extremes of the wiggle — the fault being looked for.
    val connected = kotlin.math.abs(wave(progress)) < 0.8f
    drawCircle(
        color = if (connected) accent else accent.copy(alpha = 0.12f),
        radius = h * 0.03f,
        center = Offset(w * 0.72f, h * 0.33f),
    )
}
