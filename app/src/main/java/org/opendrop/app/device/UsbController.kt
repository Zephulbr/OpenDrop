package org.opendrop.app.device

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opendrop.protocol.MoondropUsbModels
import org.opendrop.protocol.UsbDeviceInfo
import org.opendrop.protocol.UsbIdentify
import org.opendrop.protocol.UsbProbes
import org.opendrop.protocol.UsbRead
import org.opendrop.transport.usb.UsbConsole
import org.opendrop.transport.usb.UsbProbe

/** One attached USB device, by Android's device name (its bus path). */
data class UsbEntry(
    val key: String,
    val info: UsbDeviceInfo,
    val hasPermission: Boolean,
) {
    val likelyMoondrop: Boolean get() = MoondropUsbModels.looksLikeMoondrop(info.manufacturer, info.product)

    /** USB audio (DACs, DSP cables) or a recognised Moondrop device. */
    val relevant: Boolean get() = likelyMoondrop || info.interfaces.any { it.interfaceClass == USB_CLASS_AUDIO }

    /** Name for lists and screens; null when only the id is known (UIs fall back to a resource). */
    val title: String? get() = info.model?.name ?: info.product

    private companion object {
        const val USB_CLASS_AUDIO = 1
    }
}

/** A developer-console USB read on device [key] and what came back. [message] says why it failed. */
data class UsbProbeResult(
    val key: String,
    val read: UsbRead,
    val status: ProbeStatus,
    val reply: List<Byte> = emptyList(),
    val message: String? = null,
)

/**
 * Attached USB devices (roadmap M5): lists them, asks for permission and
 * reads their descriptors with [UsbProbe]. The only vendor commands are the
 * developer console's get commands ([UsbConsole]); nothing that changes a
 * setting is sent until its protocol is confirmed by a capture.
 */
class UsbController(private val context: Context) {
    private val manager = context.getSystemService(UsbManager::class.java)
    private val probe = manager?.let(::UsbProbe)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val console = manager?.let(::UsbConsole)

    private val _devices = MutableStateFlow<List<UsbEntry>>(emptyList())
    val devices: StateFlow<List<UsbEntry>> = _devices.asStateFlow()

    private val _consoleResult = MutableStateFlow<UsbProbeResult?>(null)

    /** The developer console's last USB read, if any. */
    val consoleResult: StateFlow<UsbProbeResult?> = _consoleResult.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_PERMISSION)
        }
        // NOT_EXPORTED: system attach/detach broadcasts still arrive, and the
        // permission-grant broadcast is explicit to this package.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    /** Re-lists attached devices and reads the ones OpenDrop may open. */
    fun refresh() {
        val m = manager ?: return
        val p = probe ?: return
        scope.launch(Dispatchers.IO) {
            val entries = try {
                m.deviceList.values.map { device ->
                    UsbEntry(device.deviceName, p.read(device), m.hasPermission(device))
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't list USB devices", e)
                emptyList()
            }
            _devices.update { entries.sortedWith(compareByDescending<UsbEntry> { it.likelyMoondrop }.thenBy { it.title ?: "" }) }
        }
    }

    /**
     * Developer console: sends one [UsbProbes] read to the device [key] names,
     * if the family it was identified as offers it. One at a time.
     */
    fun probe(key: String, read: UsbRead): Boolean {
        val m = manager ?: return false
        val c = console ?: return false
        val device = m.deviceList[key] ?: return false
        val entry = _devices.value.firstOrNull { it.key == key } ?: return false
        if (read !in UsbProbes.readsFor(UsbIdentify.of(entry.info).family)) return false
        if (_consoleResult.value?.status == ProbeStatus.WAITING) return false
        _consoleResult.value = UsbProbeResult(key, read, ProbeStatus.WAITING)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { c.query(device, read) }.getOrElse { UsbConsole.Result.Failed(it.message ?: it.javaClass.simpleName) }
            }
            _consoleResult.value = when (result) {
                is UsbConsole.Result.Reply -> UsbProbeResult(key, read, ProbeStatus.REPLY, result.bytes.toList())
                is UsbConsole.Result.Failed -> UsbProbeResult(key, read, ProbeStatus.ERROR, message = result.message)
            }
        }
        return true
    }

    /** Shows Android's "Allow OpenDrop to access …?" dialog; [refresh]es on the answer. */
    fun requestPermission(key: String) {
        val m = manager ?: return
        val device: UsbDevice = m.deviceList[key] ?: return
        // The system fills in EXTRA_PERMISSION_GRANTED, so the PendingIntent must
        // be mutable; it's explicit (our package), as Android 14 requires.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_PERMISSION).setPackage(context.packageName),
            flags,
        )
        m.requestPermission(device, intent)
    }

    private companion object {
        const val TAG = "UsbController"
        const val ACTION_PERMISSION = "org.opendrop.app.USB_PERMISSION"
    }
}
