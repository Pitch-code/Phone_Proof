package com.phoneproof.core.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.diagnostics.Diagnostics

/** Whether a permission is currently held. */
fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Shows [content] once [permission] is held, and explains itself first if it is not.
 *
 * ## Why the reason comes before the dialog
 *
 * `android-standards.md` requires a permission to be requested at the point of use with the reason
 * visible on screen. The system dialog cannot carry a reason — it says "Allow PhoneProof to record
 * audio?" and nothing more — so a buyer who is handed that cold, in a shop, on a stranger's phone, has
 * every reason to decline. This puts the sentence that earns the tap *above* the prompt.
 *
 * ## The three states, and why the third one needs its own handling
 *
 *  1. **Granted** — [content] is shown and this composable is invisible.
 *  2. **Not yet asked, or asked and refused once** — the rationale, and a button that asks.
 *  3. **Refused permanently.** Android stops showing the dialog at all after two refusals, so the button
 *     from state 2 becomes a button that does nothing — the single worst outcome, because the buyer taps
 *     it, sees no response, and concludes the app is broken. Detected via
 *     `shouldShowRequestPermissionRationale` returning false *after* an attempt, and answered with a
 *     route into the system settings page, which is the only way back.
 *
 * The grant is re-checked on every resume, because that route leads out of the app and back.
 */
@Composable
fun PermissionGate(
    permission: String,
    /** What the app wants to do, in the buyer's terms. Not the permission's name. */
    title: String,
    /** Why it cannot be done without this, and what the app will not do with it. */
    rationale: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var granted by remember { mutableStateOf(context.hasPermission(permission)) }
    // Survives rotation, because whether the buyer has already refused decides which button they see,
    // and losing that would put them back on a button Android has stopped answering.
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        askedOnce = true
        Diagnostics.info(TAG, "$permission ${if (result) "granted" else "refused"}")
    }

    // Re-checked on resume rather than only at first composition. The settings route below leaves the
    // app, and coming back with the permission granted has to reveal the content without a restart.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.hasPermission(permission)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) {
        content()
        return
    }

    // shouldShowRequestPermissionRationale is false both before the first ask and after a permanent
    // refusal, which is why askedOnce is needed to tell those two apart. Without it a buyer who has
    // never been asked would be sent to the settings screen for no reason.
    val permanentlyRefused = askedOnce && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            text = rationale,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )

        if (permanentlyRefused) {
            Text(
                text = "This was refused twice, so Android will not ask again. It has to be switched " +
                    "on in the phone's own settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.caution,
            )
            Button(
                onClick = { context.openAppSettings() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhoneProofTheme.colors.accent,
                    contentColor = Color.White,
                ),
            ) {
                Text("Open app settings", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(
                onClick = { launcher.launch(permission) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhoneProofTheme.colors.accent,
                    contentColor = Color.White,
                ),
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * Walks up the context wrappers to the Activity.
 *
 * Needed because `shouldShowRequestPermissionRationale` takes an Activity and `LocalContext` in Compose
 * is frequently a wrapper rather than one. Returns null rather than casting blindly, and a null only
 * costs the permanent-refusal detection — the request itself still works.
 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }.onFailure { Diagnostics.error(TAG, "could not open app settings", it) }
}

private const val TAG = "PermissionGate"
