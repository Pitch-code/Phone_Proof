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
            // SYSTEM for the first frame, because the stored value is read asynchronously. Someone
            // who picked Light on a dark-mode phone therefore gets one dark frame at cold start.
            // Accepted rather than blocking the first frame on a disk read: a brief flash is a
            // smaller cost than a slower launch on a cheap phone, which is the hardware this app
            // is most often used on.
            val themeMode by repository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

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
