package com.phoneproof.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * The title at the top of a screen, announced to a screen reader as a heading.
 *
 * Every screen had its own copy of the same three lines — `titleLarge`, `textPrimary`, done — and none of
 * them said they were a heading. TalkBack offers "navigate by heading" as its main way of skipping past
 * text, so with no headings declared the only way through a screen is to swipe through every word on it. On
 * the guide, that is eight cards of prose before reaching the first control.
 *
 * Sharing the composable also means the next screen gets this by default rather than by remembering.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = PhoneProofTheme.colors.textPrimary,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Hides a purely decorative glyph from a screen reader.
 *
 * The chevrons on navigation rows are drawn as the character "›", which TalkBack reads out — either as
 * "greater-than sign" or as nothing at all, depending on the verbosity settings. Either way it is noise
 * inside a row whose own text already says where it goes.
 *
 * `clearAndSetSemantics` rather than `contentDescription = ""`: an empty description still leaves a node in
 * the tree to be stopped on, whereas clearing it removes the node from the traversal entirely.
 */
fun Modifier.decorative(): Modifier = this.clearAndSetSemantics { }
