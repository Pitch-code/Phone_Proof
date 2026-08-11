package com.phoneproof.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.diagnostics.DiagnosticsEnvironment
import com.phoneproof.core.diagnostics.DiagnosticsReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stateful wrapper. Reads the recorder on composition rather than observing it as a flow: the log is
 * a debugging surface opened deliberately, and a live-updating list would make it impossible to read
 * the entry you just opened the screen to look at.
 */
@Composable
fun DiagnosticsRoute(
    appVersion: String,
    versionCode: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    // Bumped to force a re-read after Clear, since the recorder is not observable.
    var revision by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf(Diagnostics.recorder.entries()) }
    var dropped by remember { mutableStateOf(Diagnostics.recorder.droppedCount) }

    remember(revision) {
        entries = Diagnostics.recorder.entries()
        dropped = Diagnostics.recorder.droppedCount
        revision
    }

    val environment = remember(appVersion, versionCode) {
        environment(context, appVersion, versionCode)
    }
    val formatTimestamp: (Long) -> String = remember(timeFormat) {
        { millis -> timeFormat.format(Date(millis)) }
    }

    fun reportText(): String = DiagnosticsReport.format(
        environment = environment,
        entries = entries,
        droppedCount = dropped,
        formatTimestamp = formatTimestamp,
    )

    DiagnosticsScreen(
        entries = entries,
        droppedCount = dropped,
        header = headerLine(environment),
        onCopy = { copyToClipboard(context, reportText()) },
        onShare = { share(context, reportText()) },
        onClear = {
            Diagnostics.recorder.clear()
            revision++
        },
        formatTimestamp = formatTimestamp,
        modifier = modifier,
    )
}

private fun headerLine(environment: DiagnosticsEnvironment): String =
    "${environment.manufacturer} ${environment.model}  ·  Android ${environment.androidRelease} " +
        "(API ${environment.sdkInt})  ·  app ${environment.appVersion}"

private fun environment(
    context: Context,
    appVersion: String,
    versionCode: Long,
): DiagnosticsEnvironment {
    val metrics = context.resources.displayMetrics
    return DiagnosticsEnvironment(
        appVersion = appVersion,
        versionCode = versionCode,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        screen = "${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi}dpi",
        locale = Locale.getDefault().toLanguageTag(),
    )
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PhoneProof diagnostics", text))
    }.onFailure { Diagnostics.error(TAG, "copy to clipboard failed", it) }
}

private fun share(context: Context, text: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PhoneProof diagnostics")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share diagnostics"))
    }.onFailure {
        // Failing to share is itself worth recording: it is exactly the sort of thing that is
        // invisible otherwise, and the log survives to explain it.
        Diagnostics.error(TAG, "share failed", it)
    }
}

private const val TAG = "DiagnosticsRoute"
