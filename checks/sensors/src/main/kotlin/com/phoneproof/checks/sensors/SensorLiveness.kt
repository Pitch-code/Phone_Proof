package com.phoneproof.checks.sensors

/** What a sensor turned out to be doing, as distinct from whether the phone admits to having one. */
enum class SensorState {
    /** Responded the way physics says it should. */
    ALIVE,

    /**
     * Accepted the subscription and then delivered nothing at all.
     *
     * Held apart from [DEAD] because it needs no witness: three seconds of silence from a sensor the
     * system was happy to register is conclusive whatever the phone was doing.
     */
    SILENT,

    /** Exercised — a second sensor confirms the conditions were there — and did not respond. */
    DEAD,

    /** Delivered the identical sample over and over while the phone was being moved. */
    STUCK,

    /** Responding, but reporting a number the world does not contain. */
    IMPLAUSIBLE,

    /**
     * Nothing can be concluded, because the conditions the test needs never happened.
     *
     * The single most important state in this file. It is what stops the app telling a seller their
     * proximity sensor is broken when the buyer simply never covered it.
     */
    NOT_EXERCISED,

    /**
     * The app never managed to subscribe, so the hardware was never on trial.
     *
     * Kept apart from [NOT_EXERCISED] because the two need opposite sentences. "The phone was not moved
     * far enough" is a fair thing to tell a buyer who did not move it, and a nonsense thing to tell one
     * whose phone the app failed to connect to. This one is our failure, and says so.
     */
    UNAVAILABLE,
}

data class SensorFinding(
    val kind: SensorKind,
    val state: SensorState,
    val stats: TraceStats,
)

/**
 * Turns raw sensor traces into findings, using each sensor to vouch for the other.
 *
 * ## The problem this solves
 *
 * A presence check asks the phone what hardware it has and believes the answer. Every sensor on a
 * water-damaged handset is still listed. The obvious fix — watch the readings and fail anything that
 * does not change — is worse than useless, because a sensor that did not change and a phone that was
 * never moved look identical from inside the app. That mistake is the reason this project exists: a
 * competing app called a working proximity sensor broken during a trade-in and it cost the seller
 * money.
 *
 * ## The witness
 *
 * So nothing is ever called dead on its own evidence. Each sensor needs another one to confirm the
 * conditions were really there:
 *
 *  - **The accelerometer vouches for the gyroscope.** If the accelerometer shows the phone swung
 *    through a large angle and the gyroscope reported no rotation, the gyroscope is dead. If the
 *    accelerometer shows the phone barely moved, nothing is concluded about the gyroscope at all.
 *  - **Light and proximity vouch for each other.** One palm over the top of the phone should darken
 *    the light sensor and trip the proximity sensor. If one of them noticed and the other did not, the
 *    silent one has a problem. If neither noticed, the buyer did not do it.
 *  - **And the gyroscope vouches for the accelerometer**, which is what makes that pair symmetric. A
 *    latched accelerometer and a phone nobody picked up produce the same readings; the gyroscope
 *    reporting a turn is the only thing that separates them.
 *
 * Gravity does one job no witness can do: it proves *calibration*. An accelerometer lying still and
 * reporting 9.807 m/s² has verified itself against a known constant, which is why it is trusted to
 * vouch for the others — and why, if it is dead, the gyroscope's verdict correctly degrades to "cannot
 * tell" rather than becoming an accusation built on a broken instrument.
 */
object SensorLiveness {

    /**
     * Standard gravity is 9.807 m/s². The window is wide because the phone is in someone's hand and
     * being deliberately tilted, which adds real acceleration on top of gravity; it is not a
     * calibration bench. Anything outside it is not a slightly-off sensor, it is one reporting a
     * quantity that has nothing to do with the planet.
     */
    private const val GRAVITY_MIN = 8.0
    private const val GRAVITY_MAX = 11.5

    /** Below this a sensor is reporting zeros, whatever its driver claims. */
    private const val SILENT_MAGNITUDE = 0.5

    /**
     * Earth's magnetic field runs 25–65 µT depending on latitude. Widened at both ends because a phone
     * contains its own magnets — the speaker, the vibration motor — and because a used-phone shop is
     * full of metal shelving.
     */
    private const val EARTH_FIELD_MIN = 20.0
    private const val EARTH_FIELD_MAX = 90.0

    /**
     * A palm over the sensor has to either halve the reading or drop it by 20 lux.
     *
     * Two rules rather than one because a shop can be either. In daylight near a window the absolute
     * drop is hundreds of lux; in a dim back room the room itself is 15 lux, an absolute threshold
     * could never be met, and the ratio is what carries the test.
     */
    fun analyse(traces: List<SensorTrace>, present: Set<SensorKind>): List<SensorFinding> {
        val byKind = traces.associateBy { it.kind }
        val stats = SensorKind.entries.associateWith { kind ->
            byKind[kind]?.stats ?: TraceStats.of(emptyList())
        }
        // Absent from the traces altogether counts as unsubscribed, not as silence.
        val registered = SensorKind.entries.associateWith { byKind[it]?.registered == true }

        // Did the phone actually move? Asked of the one sensor that can be checked against a known
        // constant, so a broken witness disqualifies itself rather than condemning the gyroscope.
        val accelerometer = stats.getValue(SensorKind.ACCELEROMETER)
        val accelerometerTrustworthy = accelerometer.count > 0 &&
            accelerometer.magnitudeMean in GRAVITY_MIN..GRAVITY_MAX
        val moved = accelerometerTrustworthy && SensorGesture.tilted(accelerometer)

        // And the gyroscope vouches for the accelerometer, which is what makes the pair symmetric. A
        // latched accelerometer and a phone nobody picked up look identical from the accelerometer's
        // own readings, so without this the app would either miss the fault or invent it. The
        // gyroscope saying the handset turned is the thing that tells those two apart.
        val rotated = SensorGesture.turned(stats.getValue(SensorKind.GYROSCOPE))

        val light = stats.getValue(SensorKind.LIGHT)
        val proximityMoved = SensorGesture.proximityResponded(stats.getValue(SensorKind.PROXIMITY))
        val lightResponded = SensorGesture.lightResponded(light)
        val lightWentDark = SensorGesture.lightWentDark(light)

        return present.sortedBy { it.ordinal }.map { kind ->
            val s = stats.getValue(kind)
            SensorFinding(
                kind = kind,
                stats = s,
                state = when {
                    !registered.getValue(kind) -> SensorState.UNAVAILABLE
                    s.count == 0 -> SensorState.SILENT
                    else -> when (kind) {
                        SensorKind.ACCELEROMETER -> accelerometerState(s, rotated)
                        SensorKind.GYROSCOPE -> gyroscopeState(s, moved)
                        SensorKind.MAGNETOMETER -> magnetometerState(s, moved)
                        // Note the asymmetry, which is deliberate. Proximity reporting "near" only
                        // happens when something really is close, so it is a trustworthy witness on
                        // its own. The light sensor has to have gone properly dark before it is
                        // allowed to accuse anything.
                        SensorKind.PROXIMITY -> witnessed(
                            responded = proximityMoved,
                            witnessSawIt = lightWentDark,
                        )
                        SensorKind.LIGHT -> witnessed(
                            responded = lightResponded,
                            witnessSawIt = proximityMoved,
                        )
                    }
                },
            )
        }
    }

    /**
     * The accelerometer.
     *
     * Gravity is its own witness for *calibration* — a sensor reporting 9.8 m/s² while lying still has
     * proved something no other sensor here can prove without being waved about. But calibration is not
     * liveness, so the gyroscope is still needed to tell a latched sensor from an untouched phone.
     */
    private fun accelerometerState(s: TraceStats, rotated: Boolean): SensorState = when {
        s.magnitudeMean < SILENT_MAGNITUDE -> SensorState.SILENT
        // Wrong gravity is conclusive whether or not the phone was tilted, so it is decided before the
        // movement question is asked at all.
        s.magnitudeMean !in GRAVITY_MIN..GRAVITY_MAX -> SensorState.IMPLAUSIBLE
        SensorGesture.tilted(s) -> SensorState.ALIVE
        // Below here the readings did not move. Only the gyroscope can say whether the phone did.
        !rotated -> SensorState.NOT_EXERCISED
        s.distinctValues <= 1 -> SensorState.STUCK
        else -> SensorState.DEAD
    }

    private fun gyroscopeState(s: TraceStats, moved: Boolean): SensorState = when {
        SensorGesture.turned(s) -> SensorState.ALIVE
        !moved -> SensorState.NOT_EXERCISED
        s.distinctValues <= 1 -> SensorState.STUCK
        else -> SensorState.DEAD
    }

    private fun magnetometerState(s: TraceStats, moved: Boolean): SensorState = when {
        s.magnitudeMean < SILENT_MAGNITUDE -> SensorState.SILENT
        s.magnitudeMean !in EARTH_FIELD_MIN..EARTH_FIELD_MAX -> SensorState.IMPLAUSIBLE
        // Only while the phone was turning. A compass held still in a steady field can legitimately
        // repeat itself, and calling that "stuck" would invent a fault out of a quiet room.
        moved && s.distinctValues <= 1 -> SensorState.STUCK
        else -> SensorState.ALIVE
    }

    /** Light and proximity, each judged only against what the other one saw. */
    private fun witnessed(
        responded: Boolean,
        witnessSawIt: Boolean,
    ): SensorState = when {
        responded -> SensorState.ALIVE
        witnessSawIt -> SensorState.DEAD
        else -> SensorState.NOT_EXERCISED
    }
}
