package com.phoneproof.feature.imei

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.phoneproof.checks.imei.Imei
import com.phoneproof.checks.imei.ImeiCheck
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult

/**
 * Stateful entry point.
 *
 * No ViewModel and no persistence, both deliberately. The check is a pure function of fifteen digits,
 * so there is no asynchronous work to survive; and an IMEI is somebody else's device identifier, which
 * this app has no reason to keep after the buyer has walked away from the stall.
 *
 * `rememberSaveable` rather than `remember`, so a rotation or the keyboard resizing the window does not
 * discard a half-typed number — the precise irritation that would make someone give up on a
 * fifteen-digit form. Held locally and never round-tripped through storage, which is the lesson from
 * the shop-name field: a text field whose value comes back from a write is a text field that fights the
 * person typing in it.
 */
@Composable
fun ImeiRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var typed by rememberSaveable { mutableStateOf("") }

    // Only once all fifteen digits are in. The check is happy to judge a partial number — the screen
    // shows it doing so as the buyer types — but a run must not record "IMEI: too short" as its finding
    // for the step and tick it off while the buyer is still on the sixth digit.
    LaunchedEffect(typed) {
        val imei = Imei.of(typed)
        if (imei.isComplete) onResults(listOf(ImeiCheck.evaluate(imei)))
    }

    ImeiScreen(
        typed = typed,
        onTypedChanged = { typed = it },
        onOpenCeir = {
            val imei = Imei.of(typed)
            // Copied on the way out, because the portal cannot be deep-linked with the number.
            //
            // Guessing a query parameter for someone else's website would be inventing an interface:
            // it would work until the portal changed, then fail silently and leave the buyer looking at
            // a blank form believing the app had filled it in. Putting the digits on the clipboard is
            // honest about the handoff and saves the retyping, which was the only real cost.
            if (imei.isComplete) {
                clipboard.setText(AnnotatedString(imei.digits))
                Diagnostics.info(TAG, "IMEI copied for the CEIR handoff")
            }
            openCeir(context)
        },
        modifier = modifier,
    )
}

/**
 * Opens the official register, and only that one.
 *
 * Hardcoded rather than configurable. `play-policy.md` is explicit that only the government portal may
 * be linked — the third-party IMEI-lookup sites sell reassurance rather than facts, and a buyer arriving
 * from a trust tool is precisely their market.
 */
private fun openCeir(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CEIR_URL)))
    }.onFailure { Diagnostics.error(TAG, "could not open the CEIR portal", it) }
}

private const val TAG = "ImeiRoute"

/** The Indian government's Central Equipment Identity Register. */
private const val CEIR_URL = "https://ceir.sancharsaathi.gov.in"
