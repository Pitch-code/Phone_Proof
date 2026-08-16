package com.phoneproof.feature.cameratest

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.media.CameraCheck
import com.phoneproof.checks.media.HeardTone
import com.phoneproof.checks.media.TorchCheck
import com.phoneproof.core.media.CameraInfo
import com.phoneproof.core.media.CameraProbe
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
) {
    val hasFlash: Boolean get() = cameras.any { it.hasFlash }
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
        )

        viewModelScope.launch {
            val gathered = mutableListOf<CheckResult>()
            for (camera in cameras) {
                _uiState.value = _uiState.value.copy(testing = camera.facing.label)
                // One at a time, not in parallel. Most phones cannot hold two cameras open at once, and
                // the second would fail with ERROR_MAX_CAMERAS_IN_USE — reporting a working camera as
                // unavailable because the app was in a hurry.
                gathered += CameraCheck.evaluate(probe.probe(camera))
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
