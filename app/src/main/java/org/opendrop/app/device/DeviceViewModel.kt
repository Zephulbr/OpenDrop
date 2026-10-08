package org.opendrop.app.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.opendrop.app.OpenDropApplication
import org.opendrop.protocol.EqPreset

/**
 * Screen-side handle on the app-wide [DeviceController]. Leaving the screen
 * doesn't touch the connection; that's the controller's and the service's job.
 */
class DeviceViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = (app as OpenDropApplication).controller

    val state: StateFlow<UiState> = controller.state

    fun refresh() = controller.refresh()

    fun connect(target: PairedDevice) = controller.connect(target)

    fun disconnect() = controller.disconnect()

    fun setEq(preset: EqPreset) = controller.setEq(preset)

    fun setVolume(value: Int) = controller.setVolume(value)
}
