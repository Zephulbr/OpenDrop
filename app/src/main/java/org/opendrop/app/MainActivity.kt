package org.opendrop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.opendrop.app.device.DeviceViewModel
import org.opendrop.app.ui.OpenDropApp
import org.opendrop.app.ui.OpenDropTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenDropTheme {
                OpenDropApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up permission or pairing changes made in system settings.
        viewModel.refresh()
    }
}
