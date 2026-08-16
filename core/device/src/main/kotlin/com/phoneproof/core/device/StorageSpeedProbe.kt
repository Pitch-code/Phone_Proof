package com.phoneproof.core.device

import android.content.Context
import com.phoneproof.checks.device.StorageSpeedAttempt
import com.phoneproof.checks.device.StorageSpeedCheck
import com.phoneproof.checks.device.StorageSpeedTrace
import com.phoneproof.core.diagnostics.Diagnostics
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes a test file, times it honestly, reads it back and deletes it.
 *
 * ## Where it writes, and why that is not negotiable
 *
 * [Context.cacheDir]. App-private, so nothing outside this app can be touched, and the system may reclaim it
 * on its own if the app were killed before cleaning up. The file is deleted in a `finally` regardless. This is
 * someone else's phone: the test has to be incapable of leaving anything behind and incapable of reaching
 * anything that is not ours.
 *
 * ## Why fsync is the whole measurement
 *
 * Without forcing each chunk to the chip, this would time Android's page cache — which is RAM, and every
 * phone ever made would look magnificent. `fd.sync()` after each chunk is what makes the number mean
 * anything, and it is why these figures come out well below what a benchmark app reports.
 *
 * ## Why the data is generated rather than stored
 *
 * The bytes come from a seeded [Random], so verifying the read-back means regenerating the same sequence
 * rather than keeping 64 MB in memory to compare against. A test that needed to hold its own payload in RAM
 * would be a worse citizen than the storage it is testing.
 */
class StorageSpeedProbe(private val context: Context) {

    fun freeBytes(): Long =
        runCatching { context.cacheDir.usableSpace }
            .onFailure { Diagnostics.error(TAG, "could not read free space", it) }
            .getOrDefault(0L)

    /**
     * @param onProgress called with 0f to 1f as the write and then the read progress.
     */
    suspend fun measure(onProgress: (Float) -> Unit = {}): StorageSpeedTrace =
        withContext(Dispatchers.IO) {
            val free = freeBytes()
            if (free < StorageSpeedCheck.REQUIRED_FREE_BYTES) {
                Diagnostics.info(TAG, "only $free bytes free; not writing a test file")
                return@withContext StorageSpeedTrace(
                    attempt = StorageSpeedAttempt.NOT_ENOUGH_SPACE,
                    freeBytes = free,
                )
            }

            val file = File(context.cacheDir, FILE_NAME)
            val chunk = ByteArray(CHUNK_BYTES)

            try {
                val chunkMillis = mutableListOf<Long>()

                FileOutputStream(file).use { stream ->
                    repeat(CHUNKS) { index ->
                        fill(chunk, index)
                        chunkMillis += measureTimeMillis {
                            stream.write(chunk)
                            stream.flush()
                            // The line that makes this a storage measurement rather than a memory one.
                            stream.fd.sync()
                        }
                        onProgress((index + 1) / (CHUNKS * 2f))
                    }
                }

                val readBuffer = ByteArray(CHUNK_BYTES)
                var matched = true
                val readMillis = measureTimeMillis {
                    RandomAccessFile(file, "r").use { reader ->
                        repeat(CHUNKS) { index ->
                            reader.readFully(readBuffer)
                            fill(chunk, index)
                            if (!readBuffer.contentEquals(chunk)) matched = false
                            onProgress(0.5f + (index + 1) / (CHUNKS * 2f))
                        }
                    }
                }

                val trace = StorageSpeedTrace(
                    attempt = StorageSpeedAttempt.MEASURED,
                    bytesWritten = CHUNK_BYTES.toLong() * CHUNKS,
                    writeMillis = chunkMillis.sum(),
                    readMillis = readMillis,
                    chunkBytes = CHUNK_BYTES.toLong(),
                    chunkMillis = chunkMillis,
                    readBackMatched = matched,
                    freeBytes = free,
                )
                Diagnostics.info(
                    TAG,
                    "write=${trace.writeMbPerSecond} read=${trace.readMbPerSecond} " +
                        "slowestChunk=${trace.slowestChunkMbPerSecond} matched=$matched",
                )
                trace
            } catch (error: Exception) {
                // Broad on purpose. A full disk, a killed process, an OEM quota and a permission oddity all
                // throw differently and all mean the same thing here: nothing was measured.
                Diagnostics.error(TAG, "storage speed test failed", error)
                StorageSpeedTrace(attempt = StorageSpeedAttempt.FAILED, freeBytes = free)
            } finally {
                // Always, and logged if it fails, because leaving 64 MB behind on a stranger's phone would be
                // an unforgivable way to end a test about storage.
                runCatching { if (file.exists() && !file.delete()) error("delete returned false") }
                    .onFailure { Diagnostics.error(TAG, "could not delete the test file", it) }
            }
        }

    /**
     * Fills [buffer] with the sequence for chunk [index].
     *
     * Seeded per chunk, so the read-back can regenerate exactly what should be there without the write having
     * kept a copy. Incompressible on purpose: a run of zeroes would let a chip with transparent compression
     * report a speed it cannot sustain on real data.
     */
    private fun fill(buffer: ByteArray, index: Int) {
        Random(SEED + index).nextBytes(buffer)
    }

    private companion object {
        const val TAG = "StorageSpeed"
        const val FILE_NAME = "phoneproof-speed-test.bin"

        /**
         * 4 MB, sixteen times: 64 MB in total.
         *
         * Chunks big enough that the timing is not dominated by the cost of a sync, and enough of them that a
         * stall shows up as one bad chunk rather than as a slightly worse average. 64 MB is also past the
         * point where a small write cache can flatter the result, while staying small enough to finish in a
         * few seconds and to be nothing on a phone with any space at all.
         */
        const val CHUNK_BYTES = 4 * 1024 * 1024
        const val CHUNKS = 16

        const val SEED = 20260816L
    }
}
