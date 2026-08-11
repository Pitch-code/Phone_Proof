package com.phoneproof.checks.emilock

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class EmiLockEvaluatorTest {

    private fun admin(
        pkg: String,
        label: String? = null,
        deviceOwner: Boolean = false,
        profileOwner: Boolean = false,
    ) = AdminApp(pkg, label, deviceOwner, profileOwner)

    @Test
    fun `no admins is a pass`() {
        val result = EmiLockEvaluator.evaluate(DeviceAdminSnapshot())
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.consequence).isNull()
    }

    @Test
    fun `a failed query is CAN'T TELL and never a pass`() {
        val result = EmiLockEvaluator.evaluate(DeviceAdminSnapshot(queryFailed = true))
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        // The distinction that matters: "could not check" must not look like "nothing found".
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `a device owner is a high confidence failure`() {
        val snapshot = DeviceAdminSnapshot(
            listOf(admin("com.example.locker", "Pay Lock", deviceOwner = true)),
        )
        val result = EmiLockEvaluator.evaluate(snapshot)
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.headline).contains("Pay Lock")
        assertThat(result.action).contains("Do not pay")
    }

    @Test
    fun `a device owner failure explains that a factory reset will not clear it`() {
        val snapshot = DeviceAdminSnapshot(listOf(admin("com.example.l", deviceOwner = true)))
        val result = EmiLockEvaluator.evaluate(snapshot)
        assertThat(result.consequence).contains("factory reset")
        assertThat(result.falsePositiveCauses).isNotEmpty()
    }

    @Test
    fun `a profile owner is a failure`() {
        val snapshot = DeviceAdminSnapshot(
            listOf(admin("com.corp.mdm", "WorkSuite", profileOwner = true)),
        )
        val result = EmiLockEvaluator.evaluate(snapshot)
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.headline).contains("WorkSuite")
    }

    @Test
    fun `a single admin reads as one app, not one app-bracket-s`() {
        // "1 app(s)" was on screen and it reads like unfinished software, which is corrosive in an
        // app asking a stranger to trust its verdict about money.
        val result = EmiLockEvaluator.evaluate(
            DeviceAdminSnapshot(listOf(admin("com.one", "Guard"))),
        )
        assertThat(result.headline).contains("1 app can control")
        assertThat(result.headline).doesNotContain("(s)")
    }

    @Test
    fun `a plain administrator is CAUTION rather than FAIL`() {
        // Genuinely ambiguous: this is how finance locks work and also how antivirus and
        // find-my-phone apps work. Failing it outright would kill honest deals.
        val snapshot = DeviceAdminSnapshot(listOf(admin("com.security.app", "Guard")))
        val result = EmiLockEvaluator.evaluate(snapshot)
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.action).contains("factory reset")
    }

    @Test
    fun `device owner outranks a plain admin in the same snapshot`() {
        val snapshot = DeviceAdminSnapshot(
            listOf(
                admin("com.security.app", "Guard"),
                admin("com.example.locker", "Pay Lock", deviceOwner = true),
            ),
        )
        val result = EmiLockEvaluator.evaluate(snapshot)
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.headline).contains("Pay Lock")
    }

    @Test
    fun `the package name is shown when the app label cannot be resolved`() {
        // Normal on Android 11+, where package visibility hides other apps' details.
        val snapshot = DeviceAdminSnapshot(listOf(admin("com.unknown.pkg", label = null)))
        val result = EmiLockEvaluator.evaluate(snapshot)
        assertThat(result.headline).contains("com.unknown.pkg")
    }

    @Test
    fun `a blank label falls back to the package name`() {
        val snapshot = DeviceAdminSnapshot(listOf(admin("com.blank.pkg", label = "  ")))
        assertThat(EmiLockEvaluator.evaluate(snapshot).headline).contains("com.blank.pkg")
    }

    @Test
    fun `multiple plain admins are counted and all named`() {
        val snapshot = DeviceAdminSnapshot(
            listOf(admin("a.one", "One"), admin("b.two", "Two")),
        )
        val result = EmiLockEvaluator.evaluate(snapshot)
        assertThat(result.headline).contains("2 apps")
        assertThat(result.headline).contains("One")
        assertThat(result.headline).contains("Two")
    }

    @Test
    fun `measurements always report the three counts`() {
        val snapshot = DeviceAdminSnapshot(
            listOf(admin("x", deviceOwner = true), admin("y")),
        )
        val labels = EmiLockEvaluator.evaluate(snapshot).measurements.map { it.label }
        assertThat(labels).containsExactly("Admin apps found", "Device owners", "Profile owners")
    }

    @Test
    fun `check id is stable so saved reports keep comparing correctly`() {
        assertThat(EmiLockEvaluator.evaluate(DeviceAdminSnapshot()).id)
            .isEqualTo("security.device_admin_lock")
    }

    @Test
    fun `a blank package name is rejected outright`() {
        assertThat(runCatching { AdminApp("  ") }.isFailure).isTrue()
    }

    // --- The null-versus-empty distinction, which a real device got wrong. ---
    //
    // DevicePolicyManager.getActiveAdmins() returns null when there are no administrators. That
    // null was being read as "the query failed", so a clean phone reported "can't tell" instead of
    // passing — the least useful possible answer for someone standing there with cash. These tests
    // pin the semantics down so it cannot drift back.

    @Test
    fun `an empty list means the platform answered and found nothing - that is a pass`() {
        val snapshot = DeviceAdminSnapshot.from(emptyList())
        assertThat(snapshot.queryFailed).isFalse()
        assertThat(EmiLockEvaluator.evaluate(snapshot).outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `null means the platform could not be asked - that is CAN'T TELL`() {
        val snapshot = DeviceAdminSnapshot.from(null)
        assertThat(snapshot.queryFailed).isTrue()
        assertThat(EmiLockEvaluator.evaluate(snapshot).outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `a populated list is carried through unchanged`() {
        val admins = listOf(admin("com.a", "A"), admin("com.b", deviceOwner = true))
        val snapshot = DeviceAdminSnapshot.from(admins)
        assertThat(snapshot.queryFailed).isFalse()
        assertThat(snapshot.admins).isEqualTo(admins)
        assertThat(EmiLockEvaluator.evaluate(snapshot).outcome).isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `empty and null produce genuinely different outcomes`() {
        // The whole point: these two must never collapse into the same answer.
        val fromEmpty = EmiLockEvaluator.evaluate(DeviceAdminSnapshot.from(emptyList())).outcome
        val fromNull = EmiLockEvaluator.evaluate(DeviceAdminSnapshot.from(null)).outcome
        assertThat(fromEmpty).isNotEqualTo(fromNull)
    }
}
