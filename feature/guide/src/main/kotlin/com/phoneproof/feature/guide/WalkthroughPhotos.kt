package com.phoneproof.feature.guide

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.phoneproof.core.diagnostics.Diagnostics
import java.io.File

/**
 * The photographs a buyer takes while working through the manual checks.
 *
 * ## Why photographs at all
 *
 * These eight checks are the ones the app cannot make, so its output for them has always been words. A
 * photograph of the water sticker, or of a lifted screen edge, is the one piece of evidence a buyer can
 * hold afterwards — before money changes hands it is something to point at while negotiating, and after
 * it is the only record of what the handset looked like at the time.
 *
 * ## Where they live, and where they deliberately do not
 *
 * App-internal storage, one file per step. **Not** the shared gallery, and that is a privacy decision
 * rather than a convenience one: the home screen promises that test results stay on this device, and
 * silently dropping photographs of a stranger's phone into the buyer's camera roll — where a backup
 * service would then upload them — would make that line false.
 *
 * Named by step id rather than by timestamp, so there is exactly one photograph per check and taking
 * another replaces it. A gallery of seventeen attempts at the SIM tray is not evidence, it is a mess, and
 * the buyer would have to curate it in a shop with someone waiting.
 *
 * ## Why this class is in a feature module
 *
 * `core:reports` would be the natural home and cannot be: it is a pure-Kotlin JVM module, and this needs
 * `Context`, `FileProvider` and `Uri`. Rather than add an Android module for one small class used by one
 * screen, it sits with the screen — following `feature:settings`, which stores the shop logo the same way.
 */
class WalkthroughPhotos(private val context: Context) {

    private val directory: File get() = File(context.filesDir, DIRECTORY)

    private fun fileFor(stepId: String): File = File(directory, "$stepId.jpg")

    /** Absolute paths of the photographs that exist, keyed by step id. */
    fun all(): Map<String, String> = runCatching {
        directory.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.associate { it.nameWithoutExtension to it.absolutePath }
            .orEmpty()
    }.onFailure { Diagnostics.error(TAG, "listing walkthrough photos failed", it) }
        .getOrDefault(emptyMap())

    /**
     * A URI the camera app may write to, creating the file if needed.
     *
     * Goes through `FileProvider` because a bare `file://` URI has not been accepted across an intent
     * boundary since Android 7 — it raises `FileUriExposedException`. The grant is scoped to this one file
     * and lasts for the one intent.
     */
    fun captureTarget(stepId: String): Uri? = runCatching {
        directory.mkdirs()
        val file = fileFor(stepId)
        if (!file.exists()) file.createNewFile()
        FileProvider.getUriForFile(context, "${context.packageName}.$AUTHORITY_SUFFIX", file)
    }.onFailure { Diagnostics.error(TAG, "could not prepare a capture target", it) }.getOrNull()

    /**
     * Removes an empty file left behind by a cancelled capture.
     *
     * [captureTarget] has to create the file before the camera app can be given a URI for it, so
     * cancelling leaves a zero-byte file. Left alone it would appear in [all] as a photograph and render as
     * a broken thumbnail — which reads as a bug in the app rather than as a cancelled action.
     */
    fun discardIfEmpty(stepId: String) {
        val file = fileFor(stepId)
        if (file.exists() && file.length() == 0L) {
            runCatching { file.delete() }
                .onFailure { Diagnostics.warn(TAG, "could not remove an empty capture", it) }
        }
    }

    fun delete(stepId: String) {
        runCatching { fileFor(stepId).delete() }
            .onFailure { Diagnostics.warn(TAG, "could not delete a photo", it) }
    }

    /**
     * An intent that shares one photograph.
     *
     * Read permission is granted on the intent rather than by making the file world-readable, so the
     * receiving app gets this one image and nothing else in the directory.
     */
    fun shareIntent(stepId: String): Intent? {
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.$AUTHORITY_SUFFIX",
                fileFor(stepId),
            )
        }.onFailure { Diagnostics.error(TAG, "could not build a share URI", it) }.getOrNull()
            ?: return null

        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share this photo",
        )
    }

    companion object {
        private const val TAG = "WalkthroughPhotos"
        private const val DIRECTORY = "walkthrough"

        /** Must match the authority in this module's manifest. */
        const val AUTHORITY_SUFFIX = "walkthroughphotos"

        /**
         * Decodes a photograph down to something a thumbnail can use.
         *
         * Downsampled while decoding rather than after. A 12-megapixel JPEG is about 48 MB as a bitmap and
         * decoding eight of them at full size to draw eight thumbnails is a reliable way to run a cheap
         * phone out of memory — on the screen whose whole purpose is inspecting cheap phones.
         *
         * `inSampleSize` is powers of two only, which is why this halves in a loop rather than computing a
         * ratio: anything else is rounded down by the decoder and quietly ignored.
         */
        fun decodeThumbnail(path: String, targetWidth: Int): android.graphics.Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2

            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.onFailure { Diagnostics.warn(TAG, "could not decode $path", it) }.getOrNull()
    }
}
