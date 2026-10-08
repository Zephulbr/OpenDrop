package org.opendrop.app.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.opendrop.app.OpenDropApplication
import org.opendrop.app.phoneeq.PhoneEqState
import org.opendrop.app.settings.Behavior
import org.opendrop.dsp.EqCurve
import org.opendrop.protocol.EqPreset

/**
 * Screen-side handle on the app-wide [DeviceController]. Leaving the screen
 * doesn't touch the connection; that's the controller's and the service's job.
 */
class DeviceViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = (app as OpenDropApplication).controller
    private val settings = (app as OpenDropApplication).settings
    private val phoneEqStore = (app as OpenDropApplication).phoneEq
    private val usb = (app as OpenDropApplication).usb

    val state: StateFlow<UiState> = controller.state

    val behavior: StateFlow<Behavior> = settings.state

    fun updateBehavior(transform: (Behavior) -> Behavior) = settings.update(transform)

    val phoneEq: StateFlow<PhoneEqState> = phoneEqStore.state

    val phoneEqFailed: StateFlow<Boolean> = phoneEqStore.failed

    fun updatePhoneEq(transform: (PhoneEqState) -> PhoneEqState) = phoneEqStore.update(transform)

    fun setCustomEq(curve: EqCurve) = phoneEqStore.setCustom(curve)

    fun refresh() {
        controller.refresh()
        usb.refresh()
    }

    val usbDevices: StateFlow<List<UsbEntry>> = usb.devices

    fun requestUsbPermission(key: String) = usb.requestPermission(key)

    fun connect(target: PairedDevice) = controller.connect(target)

    fun disconnect() = controller.disconnect()

    fun setEq(preset: EqPreset) = controller.setEq(preset)

    fun setVolume(value: Int) = controller.setVolume(value)
}
