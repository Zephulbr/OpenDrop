package org.opendrop.app.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.opendrop.app.OpenDropApplication
import org.opendrop.app.phoneeq.PhoneEqState
import org.opendrop.app.settings.Behavior
import org.opendrop.dsp.EqCurve
import org.opendrop.protocol.DevRead
import org.opendrop.protocol.EqPreset
import org.opendrop.protocol.UsbRead

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

    val usbProbe: StateFlow<UsbProbeResult?> = usb.consoleResult

    /** Developer console; sends nothing unless developer options are on. */
    fun probeUsb(key: String, read: UsbRead) {
        if (settings.state.value.developerOptions) usb.probe(key, read)
    }

    fun connect(target: PairedDevice) = controller.connect(target)

    fun disconnect() = controller.disconnect()

    fun setEq(preset: EqPreset) = controller.setEq(preset)

    fun setVolume(value: Int) = controller.setVolume(value)

    /** Developer console; sends nothing unless developer options are on. */
    fun probe(read: DevRead) {
        if (settings.state.value.developerOptions) controller.probe(read)
    }

    /** Developer console: preset 63 for 15 s, then back. */
    fun tryUserEq() {
        if (settings.state.value.developerOptions) controller.tryUserEq()
    }
}
