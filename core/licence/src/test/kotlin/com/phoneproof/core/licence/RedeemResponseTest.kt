package com.phoneproof.core.licence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the buyer gets told, for every answer the server can give.
 *
 * This is the tested half of the client on purpose. The socket cannot be exercised here without a dependency
 * I declined to add, so the part that decides what a person reads while a seller waits is kept pure — and
 * the part that cannot be tested is kept short enough to check by eye.
 *
 * The malformed-server cases are not padding. A screen that crashes on an unexpected body is a screen that
 * crashes in front of a seller, having possibly already spent a pass.
 */
class RedeemResponseTest {

    private val code = "N6WEDKZE"
    private val expiry = 1_800_086_400_000L

    @Test
    fun a_granted_pass_carries_its_expiry_and_the_count() {
        val result = parseRedeemResponse(
            200,
            """{"ok":true,"expiresAtEpochMs":$expiry,"passesLeft":4,"alreadyActive":false}""",
            code,
        )

        assertThat(result).isInstanceOf(RedeemResult.Granted::class.java)
        val granted = result as RedeemResult.Granted
        assertThat(granted.pass.expiresAtEpochMs).isEqualTo(expiry)
        assertThat(granted.pass.code).isEqualTo(code)
        assertThat(granted.passesLeft).isEqualTo(4)
        assertThat(granted.alreadyActive).isFalse()
    }

    @Test
    fun reopening_the_same_phone_is_reported_as_costing_nothing() {
        // The rule that stops the model feeling like a trap, carried all the way to the screen so it can say
        // so. Being charged for reopening an app you closed by accident is a small unfairness people
        // remember longer than the price.
        val result = parseRedeemResponse(
            200,
            """{"ok":true,"expiresAtEpochMs":$expiry,"passesLeft":4,"alreadyActive":true}""",
            code,
        )

        assertThat((result as RedeemResult.Granted).alreadyActive).isTrue()
    }

    @Test
    fun success_without_an_expiry_is_treated_as_a_server_problem() {
        // Trusting it would leave the phone holding a pass that expired in 1970 — which reads as "your code
        // did nothing" *after* it has been spent. Better to say something went wrong at our end.
        val result = parseRedeemResponse(200, """{"ok":true,"passesLeft":4}""", code)

        assertThat(result).isEqualTo(RedeemResult.ServerProblem)
    }

    @Test
    fun each_refusal_is_told_apart_by_its_reason() {
        // Read from `reason`, not the status: two different 409s would otherwise be indistinguishable, and
        // the server names its refusals precisely so the screen can give different advice.
        assertThat(parseRedeemResponse(409, """{"ok":false,"reason":"exhausted","passesLeft":0}""", code))
            .isEqualTo(RedeemResult.Exhausted(0))
        assertThat(parseRedeemResponse(404, """{"ok":false,"reason":"unknown"}""", code))
            .isEqualTo(RedeemResult.Unknown)
        assertThat(parseRedeemResponse(400, """{"ok":false,"reason":"malformed"}""", code))
            .isEqualTo(RedeemResult.Malformed)
    }

    @Test
    fun an_unrecognised_reason_is_a_server_problem_rather_than_a_guess() {
        // A future server growing a new refusal must not have it silently reported as one of the existing
        // ones. Telling a buyer their code is exhausted when it is not would be worse than admitting
        // ignorance.
        assertThat(parseRedeemResponse(418, """{"ok":false,"reason":"teapot"}""", code))
            .isEqualTo(RedeemResult.ServerProblem)
    }

    @Test
    fun nothing_here_throws_on_a_body_a_healthy_server_would_never_send() {
        // The screen behind this is read while a seller waits, possibly after a pass has been spent. A crash
        // is the worst available outcome, so every one of these must simply return something sayable.
        listOf(
            "",
            "not json at all",
            "{",
            """{"ok":true}""",
            """{"expiresAtEpochMs":"not a number"}""",
            """{"reason":}""",
            """{"passesLeft":9999999999999999999999}""",
            """{"ok":false,"reason":null}""",
        ).forEach { body ->
            listOf(200, 400, 404, 409, 500).forEach { status ->
                val result = parseRedeemResponse(status, body, code)
                assertThat(result).isNotNull()
            }
        }
    }

    @Test
    fun a_negative_expiry_is_still_read_rather_than_silently_dropped() {
        // Nonsense, but reading it means the pass is simply already expired and the app behaves as if there
        // is none — which is the safe direction. Dropping it would produce a Granted with no expiry at all.
        val result = parseRedeemResponse(200, """{"expiresAtEpochMs":-5,"passesLeft":1}""", code)

        assertThat(result).isInstanceOf(RedeemResult.Granted::class.java)
        assertThat((result as RedeemResult.Granted).pass.isActiveAt(0L)).isFalse()
    }
}
