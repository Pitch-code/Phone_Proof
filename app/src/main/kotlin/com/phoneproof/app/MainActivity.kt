package com.phoneproof.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.designsystem.theme.resolvesToDark
import com.phoneproof.core.preferences.SettingsRepository

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge to edge is not cosmetic here: the touch test has to reach the physical edges of
        // the screen, because the edges are where dead strips usually are.
        enableEdgeToEdge()

        setContent {
            val repository = remember { SettingsRepository(applicationContext) }
            // LIGHT for the first frame, matching the stored default, because the stored value is
            // read asynchronously. Whoever has not changed the theme therefore sees no flash at all,
            // which is most people; someone who chose Dark gets one light frame at cold start.
            //
            // That trade is deliberately the way round it is. Blocking the first frame on a disk read
            // would slow every launch on the cheap hardware this app is most used on, and a flash for
            // the minority who changed the setting is the cheaper cost.
            val themeMode by repository.themeMode.collectAsState(initial = ThemeMode.LIGHT)

            // Tell Android which colour to draw the status and navigation bar icons in.
            //
            // Without this the system keeps the light (white) icons that suit a dark app, and since
            // light became the default that left a white status bar with white icons on it — nothing
            // readable but the battery, which draws its own shape. enableEdgeToEdge() puts our
            // content under those bars, so their contrast is ours to get right.
            //
            // Driven by the same resolvesToDark() the theme uses, so the icons cannot disagree with
            // the background behind them, including when the choice is "match my phone".
            val darkTheme = themeMode.resolvesToDark()
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            PhoneProofTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PhoneProofTheme.colors.background,
                ) {
                    PhoneProofNavHost(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
