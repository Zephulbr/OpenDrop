package org.opendrop.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opendrop.app.device.DeviceViewModel
import org.opendrop.app.settings.AppearanceViewModel
import org.opendrop.app.ui.OpenDropApp
import org.opendrop.app.ui.theme.LocalDarkTheme
import org.opendrop.app.ui.theme.OpenDropTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DeviceViewModel by viewModels()
    private val appearanceViewModel: AppearanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by appearanceViewModel.state.collectAsStateWithLifecycle()
            // The window background (themes.xml) shows for the few ms until settings load.
            appearance?.let { current ->
                OpenDropTheme(current) {
                    val dark = LocalDarkTheme.current
                    DisposableEffect(dark) {
                        // System bar icons follow the app theme, which can differ from the system one.
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                        )
                        onDispose {}
                    }
                    OpenDropApp(viewModel, current, appearanceViewModel::update)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up permission or pairing changes made in system settings.
        viewModel.refresh()
    }

    private companion object {
        // Same scrims androidx.activity uses for 3-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
