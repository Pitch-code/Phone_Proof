package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import java.util.Locale

/** Why the app could not get as far as timing anything. */
enum class StorageSpeedAttempt {
    /** Not enough room to write the test file. Nothing is measured and nothing is filled. */
    NOT_ENOUGH_SPACE,

    /** The write or the read failed outright. */
    FAILED,

    MEASURED,
}

/**
 * What happened while a test file was written, flushed, read back and deleted.
 *
 * @param chunkMillis how long each chunk took. The interesting part: an average hides a stall, and a stall
 *   is what a worn chip actually does.
 */
data class StorageSpeedTrace(
    val attempt: StorageSpeedAttempt,
    val bytesWritten: Long = 0L,
    val writeMillis: Long = 0L,
    val readMillis: Long = 0L,
    val chunkBytes: Long = 0L,
    val chunkMillis: List<Long> = emptyList(),
    /** Whether every byte read back matched what was written. */
    val readBackMatched: Boolean = true,
    val freeBytes: Long = 0L,
) {
    val writeMbPerSecond: Double get() = throughput(bytesWritten, writeMillis)
    val readMbPerSecond: Double get() = throughput(bytesWritten, readMillis)

    private val chunkSpeeds: List<Double>
        get() = chunkMillis.map { throughput(chunkBytes, it) }.filter { it > 0.0 }

    /** The typical chunk, which is what an average would have reported. */
    val medianChunkMbPerSecond: Double
        get() = chunkSpeeds.sorted().let { sorted ->
            when {
                sorted.isEmpty() -> 0.0
                sorted.size % 2 == 1 -> sorted[sorted.size / 2]
                else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
            }
        }

    val slowestChunkMbPerSecond: Double get() = chunkSpeeds.minOrNull() ?: 0.0

    /**
     * How many times slower the worst chunk was than the typical one.
     *
     * The number an average cannot show. A chip that writes at 90 MB/s and then stops dead for two seconds
     * still averages respectably, and it is the stall a person actually feels.
     */
    val stallFactor: Double
        get() = if (slowestChunkMbPerSecond <= 0.0) {
            Double.MAX_VALUE
        } else {
            medianChunkMbPerSecond / slowestChunkMbPerSecond
        }

    private fun throughput(bytes: Long, millis: Long): Double =
        if (millis <= 0L || bytes <= 0L) 0.0 else bytes / 1_000_000.0 / (millis / 1000.0)
}

/**
 * How fast is the storage, and does it give back what it was given?
 *
 * ## What this can and cannot catch
 *
 * The famous storage fraud is a chip that claims 256 GB and holds 32. **This check does not detect that, and
 * says so.** Proving it would mean writing 256 GB to a stranger's phone, which is an hour of work, fills
 * their handset, and is not something an app should ever do. Anyone claiming to verify capacity from a
 * sandboxed app in a few seconds is guessing.
 *
 * What it does catch is the other half of the same fraud, and the more common one: **flash that is genuine in
 * size and rubbish in quality.** Recycled chips, factory rejects sold as new, and worn-out eMMC in a
 * four-year-old handset all present full capacity and write at a fraction of the speed they should. That is
 * measurable in seconds, and it is what makes a phone feel broken — apps that take ten seconds to open,
 * photos that hang while saving, updates that fail halfway.
 *
 * ## Two things worth measuring, and one of them is not the average
 *
 * The average is the obvious number and the least interesting. A chip that writes at 90 MB/s and then stalls
 * dead for two seconds averages respectably while being unusable, and the stall is what a person feels. So
 * every chunk is timed separately and the worst one is compared against the typical one.
 *
 * The other is **whether the bytes came back**. Storage that accepts a write and returns something else is
 * failing in the most serious way there is, and unlike everything else here it is not a matter of degree —
 * so it is the one outcome in this check that is a flat failure.
 *
 * ## Why fsync is not optional
 *
 * Without forcing the data to the chip, this would measure the speed of Android's page cache, which is to say
 * the speed of RAM. Every phone would look magnificent. The probe flushes and syncs each chunk, which is why
 * the numbers here are lower than a benchmark app's and mean considerably more.
 */
object StorageSpeedCheck {

    const val CHECK_ID: String = "hardware.storage_speed"

    private const val TITLE = "Storage speed"

    /**
     * Below this, something is wrong with the flash.
     *
     * 12 MB/s. For scale: a worn-out eMMC from 2015 manages 20 to 40, a current budget phone 80 to 150, and
     * anything with UFS several hundred. Deliberately far below even the oldest respectable hardware, because
     * a synced write on a busy phone with file-based encryption is much slower than a benchmark figure, and
     * this number has to be a genuine outlier rather than a slow afternoon.
     */
    const val SLOW_WRITE_MB_PER_SECOND: Double = 12.0

    /**
     * The worst chunk being this many times slower than the typical one counts as a stall.
     *
     * Six. Ordinary variation between chunks on healthy flash is well under two — the background of a phone
     * is busy, but not that busy. A factor of six is a chip stopping to think, which is what failing flash and
     * an exhausted write cache both do.
     */
    const val STALL_FACTOR: Double = 6.0

    /** Enough headroom that the test cannot be the thing that fills someone's phone. */
    const val REQUIRED_FREE_BYTES: Long = 400L * 1024 * 1024

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A phone busy installing updates or backing up writes far more slowly than an idle one.",
        "Almost-full storage slows every write, on any phone, because there is nowhere tidy to put it.",
        "Battery savers and thermal throttling both cut storage speed noticeably.",
        "File-based encryption makes every write slower, and some older chips handle it especially badly.",
    )

    fun evaluate(trace: StorageSpeedTrace): CheckResult {
        val measurements = buildList {
            if (trace.attempt == StorageSpeedAttempt.MEASURED) {
                add(Measurement("Write speed", format(trace.writeMbPerSecond), "MB/s"))
                add(Measurement("Read speed", format(trace.readMbPerSecond), "MB/s"))
                add(Measurement("Typical chunk", format(trace.medianChunkMbPerSecond), "MB/s"))
                add(Measurement("Slowest chunk", format(trace.slowestChunkMbPerSecond), "MB/s"))
                add(Measurement("Written and verified", "${trace.bytesWritten / (1024 * 1024)}", "MB"))
            }
            add(Measurement("Free space", "${trace.freeBytes / (1024 * 1024 * 1024)}", "GB"))
        }

        when (trace.attempt) {
            StorageSpeedAttempt.NOT_ENOUGH_SPACE -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "Not enough free space to run a write test without filling the phone.",
                // A fact about the handset rather than a failure, and worth saying: a phone with no room
                // left is slow at everything regardless of how good its chip is.
                action = "A phone this full will feel slow whatever its storage is capable of. Worth " +
                    "asking the seller to clear it and testing again.",
                measurements = measurements,
            )

            StorageSpeedAttempt.FAILED -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "The test file could not be written, so nothing was measured.",
                action = "Try again. If it keeps failing, that is worth knowing on its own — check " +
                    "the phone can save a photo and install an app.",
                measurements = measurements,
            )

            StorageSpeedAttempt.MEASURED -> Unit
        }

        // Bytes that came back different are not a matter of degree. This is the only flat failure in the
        // check, and it is the strongest single finding the app can produce about storage.
        if (!trace.readBackMatched) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "The storage gave back different bytes from the ones written to it.",
                consequence = "This is data loss, happening now. Photos will corrupt, apps will " +
                    "crash on launch, and an update will eventually brick the phone. It is the " +
                    "signature of failing or counterfeit flash.",
                action = "Do not buy this phone. Nothing about it can be trusted while the storage " +
                    "is returning the wrong data, including everything else in this report.",
                measurements = measurements,
                falsePositiveCauses = listOf(
                    "A phone that lost power or was force-restarted mid-test can leave a partial file.",
                    "Aggressive task killers can cut the write short and truncate what is read back.",
                ),
            )
        }

        val slow = trace.writeMbPerSecond < SLOW_WRITE_MB_PER_SECOND
        val stalling = trace.stallFactor >= STALL_FACTOR

        if (slow) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Writes ran at ${format(trace.writeMbPerSecond)} MB/s, which is very slow " +
                    "for any phone.",
                consequence = "This is what makes a phone feel broken rather than old: apps taking " +
                    "several seconds to open, the camera hanging after each shot, and system updates " +
                    "that fail partway through. It is also what recycled and counterfeit flash looks " +
                    "like — genuine in size, poor in quality.",
                action = "Check nothing is installing in the background and run it again. If it stays " +
                    "this slow, walk away — storage is not repairable, and a phone with bad flash " +
                    "gets worse rather than better.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        if (stalling) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "The average was fine, but one stretch ran " +
                    "${format(trace.stallFactor)} times slower than the rest.",
                consequence = "Stalls are what a person actually notices. A chip that is quick on " +
                    "average and stops dead every few seconds gives you a phone that freezes while " +
                    "saving a photo — and averaging the speed, as most benchmarks do, hides it " +
                    "completely.",
                action = "Run it again with nothing else happening on the phone. If the stall " +
                    "repeats, treat the storage as worn: it is not repairable and it will not improve.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = "Wrote at ${format(trace.writeMbPerSecond)} MB/s and read every byte back " +
                "unchanged, with no stalls.",
            // Said on the pass as well, because a green tick here is exactly where a buyer would otherwise
            // assume the famous capacity fraud had been ruled out. It has not been.
            consequence = null,
            measurements = measurements + Measurement("Capacity checked", "no — see note"),
        )
    }

    /**
     * The sentence that keeps this check honest, for the screen to show alongside any outcome.
     *
     * Separate from the verdict because it is true regardless of the verdict, and because a buyer reading a
     * green tick is precisely the person about to assume something this test never did.
     */
    const val CAPACITY_NOTE: String =
        "This measures how good the storage is, not how much of it there is. Proving a phone really " +
            "holds the space it claims would mean writing hundreds of gigabytes to it, which no app " +
            "should do to someone else's handset — so treat a claimed capacity as unverified."

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
}
