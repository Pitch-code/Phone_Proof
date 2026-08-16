package com.phoneproof.feature.cameratest

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.media.CameraCheck
import com.phoneproof.checks.media.HeardTone
import com.phoneproof.checks.media.TorchCheck
import com.phoneproof.core.media.CameraInfo
import com.phoneproof.core.media.CameraProbe
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CameraStage {
    READY,

    /** A camera is open and frames are being collected. */
    TESTING,

    /** Every camera has a verdict. */
    CAMERAS_DONE,

    /** The torch is lit and the buyer is being asked whether they can see it. */
    TORCH_LIT,

    FINISHED,
}

@Immutable
data class CameraTestUiState(
    val stage: CameraStage = CameraStage.READY,
    val cameras: List<CameraInfo> = emptyList(),
    val results: List<CheckResult> = emptyList(),
    val torch: CheckResult? = null,
    /** Which camera is being tested right now, for the progress line. */
    val testing: String? = null,
    /**
     * The most recent frame from each camera, keyed by the label its card carries.
     *
     * Keyed on the label rather than the camera id because that is what the card is identified by on
     * screen, and the two have to agree or a buyer sees the front camera's picture above the rear
     * camera's verdict — which would be worse than showing no picture at all.
     *
     * One frame, not a history. Holding every frame from every camera would be several megabytes of
     * bitmap for no benefit; what the screen needs is the latest.
     */
    val frames: Map<String, ImageBitmap> = emptyMap(),
) {
    val hasFlash: Boolean get() = cameras.any { it.hasFlash }

    /** How far to turn a camera's frames so they are the right way up on screen. */
    fun rotationFor(label: String): Int =
        cameras.firstOrNull { it.facing.label == label }?.sensorOrientation ?: 0
}

/**
 * Drives the camera and torch tests.
 *
 * The cameras are enumerated in the constructor, which needs no permission — characteristics are public.
 * That means the screen can say *what* it is about to test before asking for anything, and "this will
 * open the front and rear cameras" is a far easier permission to grant than a blind request.
 */
class CameraTestViewModel(
    private val probe: CameraProbe,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraTestUiState(cameras = probe.inventory()))
    val uiState: StateFlow<CameraTestUiState> = _uiState.asStateFlow()

    fun testCameras() {
        val cameras = _uiState.value.cameras
        _uiState.value = _uiState.value.copy(
            stage = CameraStage.TESTING,
            results = emptyList(),
            torch = null,
            // Cleared, so a re-run never shows the previous attempt's picture next to this attempt's
            // verdict. That pairing is exactly how a buyer would end up trusting a stale frame.
            frames = emptyMap(),
        )

        viewModelScope.launch {
            val gathered = mutableListOf<CheckResult>()
            for (camera in cameras) {
                _uiState.value = _uiState.value.copy(testing = camera.facing.label)
                // One at a time, not in parallel. Most phones cannot hold two cameras open at once, and
                // the second would fail with ERROR_MAX_CAMERAS_IN_USE — reporting a working camera as
                // unavailable because the app was in a hurry.
                val label = camera.facing.label
                val stats = probe.probe(camera) { frame ->
                    // Arrives on the camera's own handler thread. MutableStateFlow is safe to write from
                    // any thread, and the frames are wanted *while* the camera is open — that is the whole
                    // point of showing them — so this deliberately does not wait for the probe to finish.
                    _uiState.value = _uiState.value.copy(
                        frames = _uiState.value.frames + (label to frame.asImageBitmap()),
                    )
                }
                gathered += CameraCheck.evaluate(stats)
                _uiState.value = _uiState.value.copy(results = gathered.toList())
            }
            _uiState.value = _uiState.value.copy(stage = CameraStage.CAMERAS_DONE, testing = null)
        }
    }

    fun lightTheTorch() {
        val flashAvailable = _uiState.value.hasFlash
        val accepted = if (flashAvailable) probe.setTorch(true) else false

        val result = TorchCheck.evaluate(flashAvailable = flashAvailable, accepted = accepted)
        _uiState.value = _uiState.value.copy(
            // Only asks when the torch is actually lit. If the platform refused, or there is no flash,
            // the verdict is already final and asking "did you see it?" would be asking about something
            // the app never attempted.
            stage = if (flashAvailable && accepted) CameraStage.TORCH_LIT else CameraStage.FINISHED,
            torch = result,
        )
    }

    fun answerLit(lit: Boolean) {
        probe.setTorch(false)
        _uiState.value = _uiState.value.copy(
            stage = CameraStage.FINISHED,
            torch = TorchCheck.evaluate(
                flashAvailable = true,
                accepted = true,
                lit = if (lit) HeardTone.YES else HeardTone.NO,
            ),
        )
    }

    fun restart() {
        probe.setTorch(false)
        _uiState.value = CameraTestUiState(cameras = probe.inventory())
    }

    /**
     * Turns the torch off on the way out.
     *
     * Without this, a buyer who leaves the screen mid-question hands back a phone with the flash blazing
     * and no obvious way to stop it — the app's own control is gone with the screen. Leaving a stranger's
     * torch on is also a fast way to heat the phone and flatten the battery it is about to be judged on.
     */
    override fun onCleared() {
        probe.setTorch(false)
        super.onCleared()
    }
}
