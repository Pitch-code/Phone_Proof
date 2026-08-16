package com.phoneproof.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import com.phoneproof.checks.media.CameraFacing
import com.phoneproof.checks.media.CameraFrameStats
import com.phoneproof.core.diagnostics.Diagnostics
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sqrt

/** A camera the phone admits to having. */
data class CameraInfo(
    val id: String,
    val facing: CameraFacing,
    val hasFlash: Boolean,
)

/**
 * Opens cameras, reads a handful of frames, and reduces them to numbers.
 *
 * ## Camera2 rather than CameraX, and no preview
 *
 * CameraX would be less code. It is also a megabyte or two in an app whose own comments cite a 13 MB
 * budget, and the product owner chose Camera2. The saving is real because this needs almost none of what
 * CameraX provides: no preview, no aspect-ratio negotiation, no lifecycle binding, no image capture to a
 * file. It needs the smallest YUV stream the sensor will produce, for about a second.
 *
 * Frames go to an [ImageReader] and never to a `SurfaceView`. A preview would be reassuring to look at
 * and would also mean the measurement depended on a composable being laid out, visible and correctly
 * sized — three more ways for it to silently measure nothing.
 *
 * ## Everything here can fail, and none of it should crash
 *
 * A camera in use by another app, an OEM that rejects a configuration, a device that reports a camera it
 * cannot open: all ordinary. Every path returns a result the check can describe in words rather than
 * throwing, because "the camera would not open" is information a buyer can act on and a crash is not.
 */
class CameraProbe(private val context: Context) {

    private val manager: CameraManager? = context.getSystemService(CameraManager::class.java)

    /**
     * The cameras present, without opening any of them.
     *
     * Needs no permission — characteristics are public — so the screen can list what it is about to test
     * before asking for anything, which is a much easier permission to grant than a blind one.
     */
    fun inventory(): List<CameraInfo> {
        val manager = manager ?: return emptyList()
        return runCatching {
            manager.cameraIdList.mapNotNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.BACK
                    CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
                    else -> CameraFacing.OTHER
                }
                CameraInfo(
                    id = id,
                    facing = facing,
                    hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                )
            }
        }.onFailure { Diagnostics.error(TAG, "could not list cameras", it) }.getOrDefault(emptyList())
    }

    /**
     * Opens one camera, collects up to [FRAMES] frames, and measures them.
     *
     * Returns stats with `framesReceived = 0` rather than null when the camera will not cooperate, so the
     * check still produces a row explaining what happened. A missing row would leave the buyer thinking
     * the camera had not been tested.
     */
    @SuppressLint("MissingPermission")
    suspend fun probe(info: CameraInfo): CameraFrameStats {
        val manager = manager ?: return empty(info)
        val thread = HandlerThread("camera-probe").apply { start() }
        val handler = Handler(thread.looper)

        // 320x240 YUV. Small on purpose: the question is whether the sensor images at all, and a full
        // resolution frame would be slower to deliver, slower to walk, and no more informative. Every
        // Camera2 device supports YUV_420_888.
        val reader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.YUV_420_888, FRAMES + 2)

        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null

        return try {
            device = openCamera(manager, info.id, handler) ?: return empty(info)
            session = createSession(device, reader.surface, handler) ?: return empty(info)

            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(reader.surface)
                // Auto-exposure on, so a dark room is not mistaken for a dead sensor: the sensor is given
                // every chance to brighten the scene before the app concludes there is nothing in it.
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }.build()

            collectFrames(session, request, reader, handler, info)
        } catch (error: Exception) {
            // Deliberately broad. Camera2 throws CameraAccessException, IllegalStateException,
            // IllegalArgumentException and SecurityException depending on the failure and the OEM, and a
            // buyer is served identically by all of them: the camera would not open.
            Diagnostics.error(TAG, "probing camera ${info.id} failed", error)
            empty(info)
        } finally {
            runCatching { session?.close() }
            runCatching { device?.close() }
            runCatching { reader.close() }
            thread.quitSafely()
        }
    }

    /**
     * Turns the torch on or off.
     *
     * `setTorchMode` needs no permission at all, which is why the flashlight test asks for nothing. Worth
     * stating because the obvious implementation — open the camera and set FLASH_MODE_TORCH — would drag
     * the CAMERA permission into a test that does not need it.
     *
     * @return whether the platform accepted the request. Not whether light came out, which no API reports.
     */
    fun setTorch(on: Boolean): Boolean {
        val manager = manager ?: return false
        val torchCamera = inventory().firstOrNull { it.hasFlash } ?: return false
        return runCatching {
            manager.setTorchMode(torchCamera.id, on)
            true
        }.onFailure { Diagnostics.warn(TAG, "setTorchMode($on) refused", it) }.getOrDefault(false)
    }

    private suspend fun openCamera(
        manager: CameraManager,
        id: String,
        handler: Handler,
    ): CameraDevice? = suspendCancellableCoroutine { continuation ->
        runCatching {
            manager.openCamera(
                id,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (continuation.isActive) continuation.resume(camera) else camera.close()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        // ERROR_CAMERA_IN_USE and ERROR_MAX_CAMERAS_IN_USE are the common two, and both
                        // mean another app has it — not a fault in the phone.
                        Diagnostics.warn(TAG, "camera $id error $error")
                        camera.close()
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
                handler,
            )
        }.onFailure {
            Diagnostics.error(TAG, "openCamera($id) threw", it)
            if (continuation.isActive) continuation.resume(null)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun createSession(
        device: CameraDevice,
        surface: android.view.Surface,
        handler: Handler,
    ): CameraCaptureSession? = suspendCancellableCoroutine { continuation ->
        runCatching {
            // The deprecated createCaptureSession, deliberately. The SessionConfiguration replacement
            // arrived in API 28 and this app supports 26, so the modern call would need a version branch
            // and two code paths for one session with one surface. Suppressed rather than duplicated.
            device.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        if (continuation.isActive) continuation.resume(configured)
                    }

                    override fun onConfigureFailed(configured: CameraCaptureSession) {
                        Diagnostics.warn(TAG, "capture session configuration failed")
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
                handler,
            )
        }.onFailure {
            Diagnostics.error(TAG, "createCaptureSession threw", it)
            if (continuation.isActive) continuation.resume(null)
        }
    }

    /**
     * Runs the repeating request until [FRAMES] frames have been measured or the wait runs out.
     *
     * Each frame's luma plane is reduced immediately and the image closed. An `ImageReader` has a fixed
     * number of buffers and stalls silently once they are all held, so keeping frames around to analyse
     * later is the classic way to make this hang with no error at all.
     */
    private suspend fun collectFrames(
        session: CameraCaptureSession,
        request: CaptureRequest,
        reader: ImageReader,
        handler: Handler,
        info: CameraInfo,
    ): CameraFrameStats = suspendCancellableCoroutine { continuation ->
        val means = mutableListOf<Float>()
        val variations = mutableListOf<Float>()
        val fingerprints = mutableListOf<Long>()
        var settled = false

        fun finish() {
            if (settled) return
            settled = true
            runCatching { session.stopRepeating() }
            if (continuation.isActive) {
                continuation.resume(summarise(info, means, variations, fingerprints))
            }
        }

        reader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                // Plane 0 of YUV_420_888 is luma: one byte of brightness per pixel, which is all this
                // needs. Chroma is skipped entirely — colour accuracy is not being judged.
                val plane = image.planes[0]
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                val measured = measureLuma(bytes, rowStride, image.width, image.height)
                means += measured.mean
                variations += measured.variation
                fingerprints += measured.fingerprint
            } catch (error: Exception) {
                Diagnostics.warn(TAG, "reading a frame failed", error)
            } finally {
                runCatching { image.close() }
            }

            if (means.size >= FRAMES) finish()
        }, handler)

        runCatching { session.setRepeatingRequest(request, null, handler) }
            .onFailure {
                Diagnostics.error(TAG, "setRepeatingRequest failed", it)
                finish()
            }

        // A wall clock as well as a frame count. A camera that opens, configures and then delivers
        // nothing would otherwise leave this suspended forever, and an app that hangs on a camera test is
        // worse than one that reports the camera as silent.
        handler.postDelayed({ finish() }, TIMEOUT_MILLIS)

        continuation.invokeOnCancellation { runCatching { session.stopRepeating() } }
    }

    private fun summarise(
        info: CameraInfo,
        means: List<Float>,
        variations: List<Float>,
        fingerprints: List<Long>,
    ): CameraFrameStats {
        if (means.isEmpty()) return empty(info)
        return CameraFrameStats(
            facing = info.facing,
            framesReceived = means.size,
            meanLuma = means.average().toFloat(),
            // The most detailed frame, not the average. Auto-exposure and auto-focus settle over the first
            // few frames, so early ones are legitimately flat on a working camera and averaging them in
            // would drag a healthy sensor toward the flat-field threshold.
            lumaVariation = variations.max(),
            framesIdentical = fingerprints.size > 1 && fingerprints.distinct().size == 1,
        )
    }

    private fun empty(info: CameraInfo) = CameraFrameStats(
        facing = info.facing,
        framesReceived = 0,
        meanLuma = 0f,
        lumaVariation = 0f,
        framesIdentical = false,
    )

    private class LumaMeasurement(val mean: Float, val variation: Float, val fingerprint: Long)

    /**
     * Mean and standard deviation of the luma plane, plus a cheap fingerprint for the frozen-frame test.
     *
     * Sampled every fourth pixel of every fourth row. A 320x240 frame is 76 800 bytes and this runs on
     * every frame on a budget phone; a sixteenth of the pixels is far more than enough to establish
     * whether an image has any structure, and the saving is real.
     *
     * `rowStride` is honoured rather than assumed equal to the width. Camera buffers are commonly padded,
     * and walking them as though they were tightly packed reads the padding as image data — which looks
     * like a column of constant pixels and drags the variation figure down on perfectly good hardware.
     */
    private fun measureLuma(bytes: ByteArray, rowStride: Int, width: Int, height: Int): LumaMeasurement {
        var sum = 0.0
        var sumOfSquares = 0.0
        var count = 0
        var fingerprint = 0L

        var row = 0
        while (row < height) {
            val rowStart = row * rowStride
            var column = 0
            while (column < width) {
                val index = rowStart + column
                if (index >= bytes.size) break
                // Bytes are unsigned brightness; Kotlin's Byte is signed, so mask before using it.
                val value = (bytes[index].toInt() and 0xFF) / 255.0
                sum += value
                sumOfSquares += value * value
                count++
                fingerprint = fingerprint * 31 + (bytes[index].toInt() and 0xFF)
                column += SAMPLE_STEP
            }
            row += SAMPLE_STEP
        }

        if (count == 0) return LumaMeasurement(0f, 0f, 0L)

        val mean = sum / count
        // Clamped at zero before the square root: floating-point cancellation can make this a hair
        // negative on a perfectly flat frame, and sqrt of that is NaN — which then compares false against
        // every threshold and passes a dead sensor.
        val variance = (sumOfSquares / count - mean * mean).coerceAtLeast(0.0)

        return LumaMeasurement(
            mean = mean.toFloat(),
            variation = sqrt(variance).toFloat(),
            fingerprint = abs(fingerprint),
        )
    }

    private companion object {
        const val TAG = "CameraProbe"
        const val WIDTH = 320
        const val HEIGHT = 240
        const val FRAMES = 8
        const val SAMPLE_STEP = 4
        const val TIMEOUT_MILLIS = 2_500L
    }
}
