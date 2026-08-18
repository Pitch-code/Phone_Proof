package com.phoneproof.core.preferences.passes

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.preferences.Entitlement
import org.junit.Test

/**
 * When a pass counts, and what it unlocks.
 *
 * The expiry is the promise the model rests on: a buyer types a paid code onto a **stranger's** phone, and
 * agrees to because nothing is left behind. Every assertion here is really about that promise.
 */
class InspectionPassTest {

    private val now = 1_800_000_000_000L

    private fun pass(expiresInMillis: Long) = InspectionPass(
        code = "N6WEDKZE",
        expiresAtEpochMs = now + expiresInMillis,
    )

    @Test
    fun a_pass_lasts_a_day() {
        // Long enough that an inspection interrupted and resumed after lunch does not cost a second pass.
        assertThat(InspectionPass.DURATION_MILLIS).isEqualTo(24 * 60 * 60 * 1000L)
    }

    @Test
    fun a_running_pass_unlocks_premium_on_a_phone_that_owns_nothing() {
        val entitlement = effectiveEntitlement(
            accountEntitlement = Entitlement.FREE,
            pass = pass(expiresInMillis = InspectionPass.HOUR_MILLIS),
            nowEpochMs = now,
        )

        assertThat(entitlement).isEqualTo(Entitlement.PREMIUM)
    }

    @Test
    fun an_expired_pass_leaves_the_phone_exactly_as_it_was() {
        // The promise, stated as a test. Not "mostly free again", not a grace period — the same answer as a
        // phone that never had a pass at all, because it belongs to someone who never agreed to anything.
        val expired = effectiveEntitlement(
            accountEntitlement = Entitlement.FREE,
            pass = pass(expiresInMillis = -1),
            nowEpochMs = now,
        )
        val never = effectiveEntitlement(
            accountEntitlement = Entitlement.FREE,
            pass = null,
            nowEpochMs = now,
        )

        assertThat(expired).isEqualTo(never)
        assertThat(expired).isEqualTo(Entitlement.FREE)
    }

    @Test
    fun the_moment_of_expiry_is_over() {
        // Exactly at the boundary it has gone. An off-by-one here would be a phone staying unlocked for a
        // whole extra tick, which is the wrong direction to be wrong in.
        val exactly = InspectionPass(code = "N6WEDKZE", expiresAtEpochMs = now)

        assertThat(exactly.isActiveAt(now)).isFalse()
        assertThat(exactly.isActiveAt(now - 1)).isTrue()
    }

    @Test
    fun a_paid_account_is_never_downgraded_by_a_missing_or_dead_pass() {
        // Someone's own phone, which they paid for outright. Passes are a separate route and must not be able
        // to take anything away.
        listOf(Entitlement.PREMIUM, Entitlement.SHOP).forEach { owned ->
            assertThat(effectiveEntitlement(owned, null, now)).isEqualTo(owned)
            assertThat(effectiveEntitlement(owned, pass(-1), now)).isEqualTo(owned)
            assertThat(effectiveEntitlement(owned, pass(InspectionPass.HOUR_MILLIS), now))
                .isEqualTo(owned)
        }
    }

    @Test
    fun a_pass_grants_the_scans_and_the_locked_checks_it_is_being_bought_for() {
        // The point of redeeming one, expressed through what the rest of the app actually asks.
        val granted = effectiveEntitlement(Entitlement.FREE, pass(InspectionPass.HOUR_MILLIS), now)

        assertThat(granted.hasUnlimitedScans).isTrue()
        assertThat(granted.hasPremiumExtras).isTrue()
        assertThat(granted.hasAdvisoryTools).isTrue()
        // But not shop branding: a pass is Premium for a day, not a dealer licence.
        assertThat(granted.hasShopBranding).isFalse()
    }

    @Test
    fun hours_left_rounds_up_so_a_working_screen_never_says_zero() {
        assertThat(pass(InspectionPass.DURATION_MILLIS).hoursLeftAt(now)).isEqualTo(24)
        assertThat(pass(InspectionPass.HOUR_MILLIS).hoursLeftAt(now)).isEqualTo(1)
        // One minute left is still an hour on the label, because "0 hours left" beside a screen that works
        // reads as a fault.
        assertThat(pass(60_000).hoursLeftAt(now)).isEqualTo(1)
        assertThat(pass(-1).hoursLeftAt(now)).isEqualTo(0)
    }
}
