package com.phoneproof.feature.storagespeed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.device.StorageSpeedCheck
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ResultActions
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * The storage speed test.
 *
 * The screen's hardest job is refusing to be the counterfeit-capacity detector a buyer will assume it is.
 * "Fake storage" means a chip claiming 256 GB and holding 32, and this test cannot prove that — so the note
 * saying so appears **before** the test runs and again beside the verdict, rather than being tucked into a
 * caveat nobody reads. Overclaiming here would be worse than not having the test.
 */
@Composable
fun StorageSpeedScreen(
    state: StorageSpeedUiState,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        if (state.stage != StorageSpeedStage.DONE) {
            Text(
                text = "Storage speed",
                style = MaterialTheme.typography.titleLarge,
                color = PhoneProofTheme.colors.textPrimary,
            )
        }

        when (state.stage) {
            StorageSpeedStage.READY -> Ready(state, onStart)
            StorageSpeedStage.RUNNING -> Running(state)
            StorageSpeedStage.DONE -> Done(state, onRestart)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Ready(state: StorageSpeedUiState, onStart: () -> Unit) {
    Text(
        text = "Writes 64 MB to the phone, forces it onto the chip, reads every byte back and " +
            "deletes it. Takes a few seconds.",
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textSecondary,
    )
    Text(
        // Says what the fault actually feels like, because "slow storage" sounds like a specification and
        // this is the thing that makes a phone seem broken.
        text = "Slow flash is what makes a phone feel broken rather than old: apps taking seconds to " +
            "open, the camera hanging after a shot, updates failing halfway. Recycled and " +
            "counterfeit chips look normal in size and behave like this.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textTertiary,
    )

    CapacityNote()

    if (!state.enoughSpace) {
        Text(
            text = "Only %.1f GB free. The test needs a little more room, and it will not fill up " +
                "someone else's phone to run.".format(state.freeGb),
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofTheme.colors.caution,
        )
    }

    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PhoneProofTheme.colors.accent,
            contentColor = PhoneProofTheme.colors.textPrimary,
        ),
    ) {
        Text("Start", style = MaterialTheme.typography.titleMedium)
    }

    Text(
        // Where it writes, stated plainly. A buyer about to let an app write to a phone they do not own is
        // entitled to know it cannot touch anything of the owner's.
        text = "The file goes in this app's own cache and is deleted afterwards. Nothing else on the " +
            "phone is touched or read.",
        style = MaterialTheme.typography.labelSmall,
        color = PhoneProofTheme.colors.textTertiary,
    )
}

@Composable
private fun Running(state: StorageSpeedUiState) {
    Text(
        text = if (state.writing) "Writing to the storage…" else "Reading it back…",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.textPrimary,
    )
    Text(
        text = if (state.writing) {
            "Each block is forced onto the chip before the next one starts, so this measures the " +
                "storage rather than the phone's memory."
        } else {
            "Checking every byte came back the same as it went in."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )

    val width by animateFloatAsState(targetValue = state.progress, label = "storage")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(PhoneProofTheme.colors.gridEmpty, RoundedCornerShape(7.dp))
            .clearAndSetSemantics {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(width.coerceIn(0f, 1f))
                .height(14.dp)
                .background(PhoneProofTheme.colors.accent, RoundedCornerShape(7.dp)),
        )
    }
    Text(
        text = "${(state.progress * 100).toInt()}%",
        style = MaterialTheme.typography.labelMedium,
        color = PhoneProofTheme.colors.textTertiary,
    )
    Text(
        text = "Leave the phone alone while this runs — anything else writing at the same time makes " +
            "the number worse than it should be.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textTertiary,
    )
}

@Composable
private fun Done(state: StorageSpeedUiState, onRestart: () -> Unit) {
    state.result?.let { CheckResultCard(it) }

    // Repeated beside the verdict, not only before the test. A buyer reading a green tick is exactly the
    // person about to conclude the capacity was checked.
    CapacityNote()

    ResultActions(retestLabel = "Test again", onRetest = onRestart)
}

@Composable
private fun CapacityNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "WHAT THIS DOES NOT CHECK",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
        Text(
            text = StorageSpeedCheck.CAPACITY_NOTE,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )
    }
}
