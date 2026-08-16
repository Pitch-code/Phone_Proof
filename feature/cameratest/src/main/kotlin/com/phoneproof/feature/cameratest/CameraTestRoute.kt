package com.phoneproof.feature.cameratest

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.media.CameraProbe
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.permissions.PermissionGate

/**
 * Stateful entry point for the camera and torch tests.
 *
 * The permission gate wraps only the part that needs it. Enumerating cameras needs nothing — the
 * characteristics are public — so the count is not what is gated; opening them is.
 */
@Composable
fun CameraTestRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Cameras and flashlight",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
        )

        PermissionGate(
            permission = Manifest.permission.CAMERA,
            title = "Opening a camera needs the camera permission",
            // Two specifics do the work here: no photograph is taken, and nothing is stored. Both are
            // true — the frames go to an in-memory reader, are reduced to three numbers, and are closed
            // immediately. On a stranger's phone in a shop, "it does not take a picture" is the sentence
            // that makes this grantable.
            rationale = "Each camera is opened for about a second to read a few frames and measure " +
                "whether the sensor is producing a live picture. No photograph is taken, nothing is " +
                "stored, and nothing is sent anywhere. Refusing is fine — every other check still " +
                "works, and a refused permission is never reported as a fault in the phone.",
        ) {
            Granted(onResults = onResults)
        }
    }
}

@Composable
private fun Granted(onResults: (List<CheckResult>) -> Unit) {
    val context = LocalContext.current
    val probe = remember(context) { CameraProbe(context) }
    val viewModel: CameraTestViewModel = viewModel { CameraTestViewModel(probe) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // One result per lens plus the torch, so this step contributes several rows to the report.
    LaunchedEffect(state.results, state.torch) {
        onResults(state.results + listOfNotNull(state.torch))
    }

    CameraTestScreen(
        state = state,
        onTestCameras = viewModel::testCameras,
        onLightTorch = viewModel::lightTheTorch,
        onAnswerLit = viewModel::answerLit,
        onRestart = viewModel::restart,
        modifier = Modifier.fillMaxSize(),
    )
}
