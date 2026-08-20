package com.phoneproof.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.licence.LicenceClient
import com.phoneproof.core.licence.RedeemResult
import com.phoneproof.core.preferences.SettingsRepository
import com.phoneproof.core.preferences.passes.DeviceFingerprint
import kotlinx.coroutines.launch

private const val TAG = "RedeemRoute"

/**
 * Redeeming a code, wired to the licence server and to storage.
 *
 * ## The order of the last two steps matters
 *
 * The pass is saved **before** the screen says it worked. If it were the other way round and the write
 * failed, the buyer would be told they had unlocked the phone while the app still refused them — having spent
 * an inspection. Saving first means the worst case is the opposite and much kinder: the pass is live and the
 * screen fails to celebrate it.
 *
 * The device fingerprint is computed here rather than in the client, because it needs a `Context` and the
 * client is deliberately kept free of Android so its response handling stays testable.
 */
@Composable
fun RedeemRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    client: LicenceClient = LicenceClient(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember(context) { SettingsRepository(context) }

    var state by remember { mutableStateOf(RedeemUiState()) }

    RedeemScreen(
        state = state,
        onTypedChanged = { typed ->
            // Clearing the previous result on the first keystroke, so a stale error does not sit under a
            // code the buyer has already corrected.
            state = state.copy(typed = typed, result = null, stage = RedeemStage.ENTERING)
        },
        onRedeem = {
            val code = state.typed
            state = state.copy(stage = RedeemStage.WORKING, result = null)
            scope.launch {
                val fingerprint = DeviceFingerprint.of(context, code)
                val result = client.redeem(code, fingerprint)

                if (result is RedeemResult.Granted) {
                    settings.saveInspectionPass(result.pass)
                    Diagnostics.info(TAG, "pass granted, ${result.passesLeft} left")
                    state = state.copy(stage = RedeemStage.DONE, result = result)
                } else {
                    // Named without the code in it: diagnostics can be exported and shared, and a pass code
                    // is worth money.
                    Diagnostics.info(TAG, "redeem refused: ${result::class.simpleName}")
                    state = state.copy(stage = RedeemStage.ENTERING, result = result)
                }
            }
        },
        onDone = onDone,
        modifier = modifier,
    )
}
