package io.github.wallwallfacer.yourhealthsync.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Privacy statement. Also the screen Health Connect opens when the user asks
 * why this app needs its permissions.
 */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    Text(
                        PRIVACY_TEXT,
                        Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }

    companion object {
        const val PRIVACY_TEXT = """Privacy statement: Your Health Sync

What it reads: your weight, body fat rate, basal metabolic rate, bone mass and body water mass from HUAWEI Health, through HUAWEI Health Kit, only after you authorize it.

What it writes: the same measurements to Android Health Connect on this phone.

Where data goes: nowhere else. The app has no server, no analytics and no ads. Data moves only between HUAWEI Health Kit and Health Connect on your device.

Your control: revoke HUAWEI access with "Revoke HUAWEI Health access" in the app. Remove the app's Health Connect access and data in the Health Connect settings.

Source code: https://github.com/wallwallfacer/your-health-sync"""
    }
}
