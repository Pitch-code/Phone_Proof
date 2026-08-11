package com.phoneproof.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.phoneproof.core.designsystem.theme.PhoneProofColors
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge to edge is not cosmetic here: the touch test has to reach the physical edges of
        // the screen, because the edges are where dead strips usually are.
        enableEdgeToEdge()

        setContent {
            PhoneProofTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PhoneProofColors.Background,
                ) {
                    PhoneProofNavHost(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
