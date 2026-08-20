package com.phoneproof.core.licence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Turning a completed purchase into a code.
 *
 * The buyer has **already paid** by the time any of this runs, which makes it the most expensive place in the
 * app to be wrong. Nothing here may throw, and nothing may report a permanent failure for something a retry
 * would fix — `/issue` is idempotent, so asking again is always safe and usually works.
 */
class IssueResponseTest {

    @Test
    fun a_code_comes_back_with_its_count() {
        val result = parseIssueResponse(
            200,
            """{"ok":true,"code":"PP-N6WE-DKZE","passes":5,"reissued":false}""",
        )

        assertThat(result).isEqualTo(
            IssueResult.Issued(code = "PP-N6WE-DKZE", passes = 5, reissued = false),
        )
    }

    @Test
    fun a_retry_reports_that_it_was_reissued_rather_than_charged_again() {
        // The normal answer when a connection dropped after paying. It matters that the app can tell, so it
        // can reassure rather than imply a second purchase happened.
        val result = parseIssueResponse(
            200,
            """{"ok":true,"code":"PP-N6WE-DKZE","passes":5,"reissued":true}""",
        )

        assertThat((result as IssueResult.Issued).reissued).isTrue()
    }

    @Test
    fun success_without_a_code_is_deferred_rather_than_celebrated() {
        // Showing an empty code to somebody who has just paid is the worst available outcome. Asking again is
        // free and safe.
        assertThat(parseIssueResponse(200, """{"ok":true,"passes":5}"""))
            .isEqualTo(IssueResult.Deferred)
    }

    @Test
    fun only_a_bad_signature_is_permanent() {
        // The one refusal a retry cannot fix, so the only one the app should stop retrying. It should be
        // unreachable for a real purchase — reaching it means tampering or a misconfigured licensing key.
        assertThat(parseIssueResponse(403, """{"ok":false,"reason":"signature"}"""))
            .isEqualTo(IssueResult.Rejected)
    }

    @Test
    fun everything_else_is_worth_asking_about_again() {
        // Including 500s. The purchase is complete and the server is idempotent, so "ask again shortly" is
        // both the safest answer and usually the true one.
        listOf(
            500 to """{"ok":false,"reason":"server"}""",
            400 to """{"ok":false,"reason":"body"}""",
            404 to """{"ok":false,"reason":"route"}""",
            418 to """{"ok":false,"reason":"teapot"}""",
        ).forEach { (status, body) ->
            assertThat(parseIssueResponse(status, body)).isEqualTo(IssueResult.Deferred)
        }
    }

    @Test
    fun a_missing_count_does_not_invent_one() {
        // Zero would be a lie about what was bought. The code is what matters; the count is a detail beside
        // it, and the screen can simply not show a number it was not given.
        val result = parseIssueResponse(200, """{"ok":true,"code":"PP-N6WE-DKZE"}""")

        assertThat((result as IssueResult.Issued).passes).isEqualTo(0)
        assertThat(result.code).isEqualTo("PP-N6WE-DKZE")
    }

    @Test
    fun nothing_here_throws_on_a_body_a_healthy_server_would_never_send() {
        listOf("", "not json", "{", """{"code":}""", """{"code":null}""", """{"passes":"five"}""")
            .forEach { body ->
                listOf(200, 403, 500).forEach { status ->
                    assertThat(parseIssueResponse(status, body)).isNotNull()
                }
            }
    }
}
