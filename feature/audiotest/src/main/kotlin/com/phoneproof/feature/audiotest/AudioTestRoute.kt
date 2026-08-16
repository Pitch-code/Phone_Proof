package com.phoneproof.feature.audiotest

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.media.AudioProbe
import com.phoneproof.core.permissions.PermissionGate

/**
 * Stateful entry point, and the app's first permission request.
 *
 * The gate wraps the whole test rather than sitting inside it, so the microphone is asked for at the
 * moment the buyer opens this screen and never on launch. `android-standards.md` requires exactly that:
 * declare nothing until the check that needs it exists, then request it at the point of use with the
 * reason visible.
 */
@Composable
fun AudioTestRoute(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Microphone and speaker",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
        )

        PermissionGate(
            permission = Manifest.permission.RECORD_AUDIO,
            title = "This test needs the microphone",
            // Says what it is for and, just as importantly, what it is not for.
            //
            // This is a stranger's phone in a shop and the buyer is being asked to grant a recording
            // permission on it. "Nothing is saved and nothing is sent" is the sentence that earns the
            // tap, and it is true: the samples exist in memory for three seconds and are never written
            // to storage. The last line matters as much — a buyer who thinks refusing will brick the
            // app will grant it resentfully, and one who knows they can refuse will trust the rest.
            rationale = "It records for three seconds to measure the microphone, then briefly again to " +
                "listen for a tone from the speaker. Nothing is saved and nothing is sent anywhere. " +
                "Refusing is fine — every other check still works, and a refused permission is never " +
                "reported as a fault in the phone.",
        ) {
            Granted()
        }
    }
}

@Composable
private fun Granted() {
    val context = LocalContext.current
    val probe = remember(context) { AudioProbe(context) }
    val viewModel: AudioTestViewModel = viewModel { AudioTestViewModel(probe) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The screen tells the buyer to turn the media volume up, and they will do it with the hardware keys
    // or from the shade. Re-reading on resume means the warning disappears once they have, rather than
    // nagging about a level that is no longer set.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshVolume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AudioTestScreen(
        state = state,
        onStartMicrophone = viewModel::startMicrophoneTest,
        onStartSpeaker = viewModel::startSpeakerTest,
        onAnswerHeard = viewModel::answerHeard,
        onRestart = viewModel::restart,
        modifier = Modifier.fillMaxSize(),
    )
}
