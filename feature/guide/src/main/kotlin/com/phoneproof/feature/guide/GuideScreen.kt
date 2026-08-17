package com.phoneproof.feature.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.MANUAL_CHECKS_TITLE
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.component.decorative
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The checks the app cannot do.
 *
 * A list of collapsed cards rather than a wizard. A wizard would force the buyer through eight steps
 * in a fixed order while a seller waits, and most of them will already know how to look at a
 * charging port. Collapsed cards let someone jump to the two they have not thought of.
 */
@Composable
fun GuideScreen(
    steps: List<GuideStep>,
    expandedId: String?,
    /**
     * Whether an opened diagram moves.
     *
     * False when the system says animations are off. Respected rather than overridden: someone who has
     * turned motion off system-wide has done so for motion sensitivity or because the phone is slow, and
     * a looping diagram is exactly the content that setting exists to suppress. They still get the
     * drawing, held at [GuideDiagram.stillFrame], which is posed per diagram to read on its own.
     *
     * Also false in every screenshot test, which is what keeps the renders deterministic.
     */
    animate: Boolean,
    /**
     * Absolute paths of the photographs already taken, keyed by step id.
     *
     * Passed in rather than read here, so this screen stays a pure function of its state and the
     * screenshot tests can render both the empty and the photographed card without touching a filesystem.
     */
    photos: Map<String, String>,
    onToggle: (String) -> Unit,
    onTakePhoto: (String) -> Unit,
    onSharePhoto: (String) -> Unit,
    onDeletePhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            ScreenTitle(MANUAL_CHECKS_TITLE)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "No app can test any of these. They need your hands, your eyes and a " +
                    "torch — and they are where the expensive faults hide.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(steps, key = { it.id }) { step ->
                StepCard(
                    step = step,
                    number = steps.indexOf(step) + 1,
                    expanded = step.id == expandedId,
                    animate = animate,
                    photoPath = photos[step.id],
                    onToggle = { onToggle(step.id) },
                    onTakePhoto = { onTakePhoto(step.id) },
                    onSharePhoto = { onSharePhoto(step.id) },
                    onDeletePhoto = { onDeletePhoto(step.id) },
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    step: GuideStep,
    number: Int,
    expanded: Boolean,
    animate: Boolean,
    photoPath: String?,
    onToggle: () -> Unit,
    onTakePhoto: () -> Unit,
    onSharePhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (expanded) {
                    PhoneProofTheme.colors.accent.copy(alpha = 0.45f)
                } else {
                    PhoneProofTheme.colors.border
                },
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onToggle)
            // The plus and minus glyph below is the only thing that said whether this card was open, and
            // a screen reader announcing "plus" does not convey that. State belongs on the control that
            // changes it, so TalkBack reads the card's own name followed by expanded or collapsed.
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 10.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PhoneProofTheme.colors.textPrimary,
                )
                Text(
                    text = step.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
            Text(
                text = if (expanded) "−" else "+",
                style = MaterialTheme.typography.titleLarge,
                color = PhoneProofTheme.colors.textTertiary,
                // Says nothing a screen reader can use; the card's stateDescription carries it instead.
                modifier = Modifier.decorative(),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Diagram(diagram = step.diagram, animate = animate)

                Label("Why it matters")
                Body(step.whyItMatters)

                Label("How to do it")
                step.howTo.forEachIndexed { index, line ->
                    Row {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhoneProofTheme.colors.textTertiary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhoneProofTheme.colors.textSecondary,
                        )
                    }
                }

                Label("Good sign")
                Text(
                    text = step.goodSign,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.pass,
                )

                Label("Bad sign")
                Text(
                    text = step.badSign,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.caution,
                )

                Label("Your photo")
                PhotoRow(
                    photoPath = photoPath,
                    onTakePhoto = onTakePhoto,
                    onSharePhoto = onSharePhoto,
                    onDeletePhoto = onDeletePhoto,
                )
            }
        }
    }
}

/**
 * The photograph for one step: take it, look at it, share it, or replace it.
 *
 * Last in the card on purpose. It is the only thing here the buyer *does* to the app rather than reads
 * from it, and putting it above the good-sign and bad-sign lines would invite photographing the thing
 * before knowing what to look for.
 */
@Composable
private fun PhotoRow(
    photoPath: String?,
    onTakePhoto: () -> Unit,
    onSharePhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
) {
    if (photoPath == null) {
        Text(
            text = "Worth photographing if you find something. It stays on this phone, and it is " +
                "what you point at while you negotiate.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )
        OutlinedButton(
            onClick = onTakePhoto,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Take a photo", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    Thumbnail(path = photoPath)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onTakePhoto,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Retake") }
        OutlinedButton(
            onClick = onSharePhoto,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Share") }
        OutlinedButton(
            onClick = onDeletePhoto,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Delete") }
    }
}

/**
 * The photograph, decoded small.
 *
 * Decoded off the main thread through `produceState`, and downsampled while decoding. A full-size JPEG
 * from a modern sensor is tens of megabytes as a bitmap, and decoding one on the main thread to draw a
 * thumbnail would stutter the scroll on exactly the cheap handsets this screen exists to inspect.
 *
 * Keyed on the path *and* the file's modification time, so retaking a photograph replaces the image on
 * screen. Keyed on the path alone, the second photograph would decode the new file, find the same key, and
 * show the old bitmap — a caching bug that looks like the camera failing to save.
 */
@Composable
private fun Thumbnail(path: String) {
    val stamp = remember(path) { java.io.File(path).lastModified() }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path, stamp) {
        value = withContext(Dispatchers.IO) {
            WalkthroughPhotos.decodeThumbnail(path, targetWidth = THUMBNAIL_WIDTH_PX)
        }
    }

    val shape = RoundedCornerShape(10.dp)
    val current = bitmap
    if (current == null) {
        // A placeholder of the same height, so the card does not jump when the decode lands.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .background(PhoneProofTheme.colors.surfaceRaised, shape),
        )
    } else {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = "The photo you took for this check",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(shape),
        )
    }
}

/** Wide enough to look sharp across a phone, small enough that eight of them are cheap. */
private const val THUMBNAIL_WIDTH_PX = 600

@Composable
private fun Diagram(diagram: GuideDiagram, animate: Boolean) {
    // Moving again, and only while this card is open.
    //
    // This composable sits inside AnimatedVisibility(visible = expanded), so the transition is created
    // when the buyer taps a step and disposed when they close it. Nothing animates on arrival, nothing
    // animates behind text nobody is reading, and eight diagrams never move at once — the motion is a
    // consequence of a deliberate tap, which is the shape the product owner asked for.
    //
    // On the rule in Motion.kt: it bans looping animation because a measurement cannot be taken next to
    // an animating surface, and the battery check is the reason. This screen takes no measurement at
    // all. Documented as an exception in design-system.md rather than left implicit, which is what was
    // actually wrong with it the first time round.
    //
    // stillFrame is still what the diagram holds when motion is switched off, and still what every
    // screenshot renders, so the poses chosen per diagram remain load-bearing.
    val progress = if (animate) {
        val transition = rememberInfiniteTransition(label = "diagram")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                // Slow on purpose: this demonstrates a physical movement, and a fast loop reads as a
                // flicker rather than as an instruction.
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "progress",
        ).value
    } else {
        diagram.stillFrame
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surfaceRaised, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        GuideDiagramCanvas(
            diagram = diagram,
            progress = progress,
            ink = PhoneProofTheme.colors.textSecondary,
            accent = PhoneProofTheme.colors.accent,
            warn = PhoneProofTheme.colors.caution,
            // The same token as the Box behind this canvas, so an occluding tray matches what it is
            // covering. If these two ever disagree the SIM tray becomes a visible block.
            surface = PhoneProofTheme.colors.surfaceRaised,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PhoneProofTheme.colors.textTertiary,
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )
}
