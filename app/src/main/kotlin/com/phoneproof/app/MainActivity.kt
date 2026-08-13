package com.phoneproof.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
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
