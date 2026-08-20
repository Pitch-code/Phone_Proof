package com.phoneproof.core.licence

import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.preferences.passes.InspectionPass
import com.phoneproof.core.preferences.passes.PassCode
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Redeems a pass code against the licence server.
 *
 * Deliberately built out of `HttpURLConnection` and no JSON library. Two requests do not justify a
 * dependency, and every dependency added to an Android app is weight in a download that people on slow
 * connections are choosing whether to accept.
 *
 * ## Where the logic lives
 *
 * Almost nothing happens in this class. The interesting part — turning a status code and a body into a
 * [RedeemResult] — is [parseRedeemResponse], which is pure and tested. That split is on purpose: a network
 * call cannot be unit-tested here without a dependency I have declined to add, so the part that decides what
 * a buyer is told is kept where it *can* be tested, and the part that cannot is kept small enough to read.
 */
class LicenceClient(private val baseUrl: String = DEFAULT_BASE_URL) {

    /**
     * Spends a pass on this phone, or returns the one already running on it.
     *
     * Rejects a mistyped code before opening a socket. The check character exists for this: someone in front
     * of an impatient seller should be told "that is not a code" immediately, not after a round trip that
     * might fail for signal reasons too, leaving them unsure which went wrong.
     */
    suspend fun redeem(code: String, deviceHash: String): RedeemResult {
        if (!PassCode.isWellFormed(code)) return RedeemResult.Malformed
        val canonical = PassCode.normalise(code) ?: return RedeemResult.Malformed

        return post(
            path = "/redeem",
            body = """{"code":"$canonical","deviceHash":"$deviceHash"}""",
        )?.let { (status, payload) ->
            parseRedeemResponse(status, payload, canonical)
        } ?: RedeemResult.Offline
    }

    /**
     * Sends [body] and returns the status and text, or null if the server could not be reached.
     *
     * Null means *offline*, and nothing else. A 500 comes back as a status, because "the server is broken"
     * and "you have no signal" call for different words on screen and the buyer can act on only one of them.
     */
    private suspend fun post(path: String, body: String): Pair<Int, String>? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            runCatching {
                connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    // Short, because a buyer is waiting with a phone in their hand. Ten seconds of a
                    // spinner is already longer than anyone will tolerate in front of a seller.
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("content-type", "application/json")
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                // Errors arrive on the error stream, and the reason we need is in that body — reading only
                // inputStream would throw and lose the very thing that tells the buyer what went wrong.
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                status to text
            }.onFailure { error ->
                when (error) {
                    is IOException -> Diagnostics.info(TAG, "licence server unreachable: ${error.message}")
                    else -> Diagnostics.error(TAG, "redeem failed unexpectedly", error)
                }
            }.also {
                runCatching { connection?.disconnect() }
            }.getOrNull()
        }

    companion object {
        private const val TAG = "LicenceClient"

        /**
         * Where the licence server lives.
         *
         * Hardcoded, and that is fine: it is a public address, not a secret, and making it configurable
         * would add a setting nobody should ever change and an attack surface where somebody could point
         * the app at a server that grants free passes.
         */
        const val DEFAULT_BASE_URL: String = "https://phoneproof-licence.veerar77.workers.dev"
    }
}

/**
 * Turns the server's answer into something the screen can say.
 *
 * Pure, and where every decision about wording lives. Kept out of the network call so it can be tested
 * exhaustively — including the responses a healthy server should never send, because those are exactly the
 * ones that would otherwise crash the screen a buyer is standing in front of.
 *
 * The `reason` field is read rather than the status code alone. Two different 409s would be
 * indistinguishable otherwise, and the server was written to name its refusals for this reason.
 */
internal fun parseRedeemResponse(status: Int, body: String, code: String): RedeemResult {
    val expiresAt = body.longField("expiresAtEpochMs")
    val passesLeft = body.longField("passesLeft")?.toInt()

    if (status in 200..299) {
        // A success without an expiry is not a success. Trusting it would leave the phone holding a pass
        // that expired in 1970, which reads as "your code did nothing" after it has been spent.
        if (expiresAt == null) {
            Diagnostics.error("LicenceClient", "server returned success with no expiry")
            return RedeemResult.ServerProblem
        }
        return RedeemResult.Granted(
            pass = InspectionPass(code = code, expiresAtEpochMs = expiresAt),
            passesLeft = passesLeft ?: 0,
            alreadyActive = body.contains("\"alreadyActive\":true"),
        )
    }

    return when (body.stringField("reason")) {
        "exhausted" -> RedeemResult.Exhausted(passesLeft ?: 0)
        "unknown" -> RedeemResult.Unknown
        "malformed" -> RedeemResult.Malformed
        else -> RedeemResult.ServerProblem
    }
}

/**
 * The smallest JSON reading that does the job.
 *
 * Not a parser, and not pretending to be one. Three fields are read from a body this project also wrote, so
 * a real parser would be a dependency bought for nothing. It is deliberately strict: anything unexpected
 * reads as absent, which every caller already has to handle.
 */
private fun String.stringField(name: String): String? =
    Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(this)?.groupValues?.get(1)

private fun String.longField(name: String): Long? =
    Regex("\"$name\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLongOrNull()
