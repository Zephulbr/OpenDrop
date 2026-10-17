package org.opendrop.app.device

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opendrop.protocol.ComtrueState
import org.opendrop.protocol.MoondropUsbModels
import org.opendrop.protocol.UsbAudioFormats
import org.opendrop.protocol.UsbDeviceInfo
import org.opendrop.protocol.UsbFamily
import org.opendrop.protocol.UsbStreamFormat
import org.opendrop.protocol.UsbIdentify
import org.opendrop.protocol.UsbProbes
import org.opendrop.protocol.UsbRead
import org.opendrop.protocol.UsbWrite
import org.opendrop.transport.usb.UsbConsole
import org.opendrop.transport.usb.UsbProbe

/** One attached USB device, by Android's device name (its bus path). */
data class UsbEntry(
    val key: String,
    val info: UsbDeviceInfo,
    val hasPermission: Boolean,
    /** What Android reports for it as an audio output, if it is one. */
    val output: UsbAudioOutput? = null,
) {
    val likelyMoondrop: Boolean get() = MoondropUsbModels.looksLikeMoondrop(info.manufacturer, info.product)

    /** Playback formats from its descriptors; empty without USB permission. */
    val formats: List<UsbStreamFormat> get() = UsbAudioFormats.parse(info.rawDescriptors)

    val family: UsbFamily? get() = UsbIdentify.of(info).family

    /** USB audio (DACs, DSP cables) or a recognised Moondrop device. */
    val relevant: Boolean get() = likelyMoondrop || info.interfaces.any { it.interfaceClass == USB_CLASS_AUDIO }

    /** Name for lists and screens; null when only the id is known (UIs fall back to a resource). */
    val title: String? get() = info.model?.name ?: info.product

    private companion object {
        const val USB_CLASS_AUDIO = 1
    }
}

/**
 * A developer-console USB read on device [key] and what came back. [message]
 * says why it failed. For a write, [write] and [value] say what was set and
 * [reply] is its read-back.
 */
data class UsbProbeResult(
    val key: String,
    val read: UsbRead,
    val status: ProbeStatus,
    val reply: List<Byte> = emptyList(),
    val message: String? = null,
    val write: UsbWrite? = null,
    val value: Int? = null,
)

/**
 * Attached USB devices (roadmap M5): lists them, asks for permission and
 * reads their descriptors with [UsbProbe]. The only vendor commands are the
 * developer console's ([UsbConsole]): get commands and a few reversible,
 * read-back-checked settings.
 */
class UsbController(private val context: Context) {
    private val manager = context.getSystemService(UsbManager::class.java)
    private val probe = manager?.let(::UsbProbe)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val console = manager?.let(::UsbConsole)
    private val audio = context.getSystemService(AudioManager::class.java)

    /** One vendor transfer sequence at a time, whoever asks. */
    private val io = Mutex()

    private val _devices = MutableStateFlow<List<UsbEntry>>(emptyList())
    val devices: StateFlow<List<UsbEntry>> = _devices.asStateFlow()

    private val _consoleResult = MutableStateFlow<UsbProbeResult?>(null)

    /** The developer console's last USB read, if any. */
    val consoleResult: StateFlow<UsbProbeResult?> = _consoleResult.asStateFlow()

    private val _dac = MutableStateFlow<Map<String, ComtrueState>>(emptyMap())

    /** Last read settings of Comtrue DACs, by device key. */
    val dac: StateFlow<Map<String, ComtrueState>> = _dac.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = refresh()
    }

    /** Android registers the audio output a moment after the USB attach; list it when it does. */
    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
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
        audio?.registerAudioDeviceCallback(audioCallback, Handler(Looper.getMainLooper()))
        refresh()
    }

    /** Re-lists attached devices and reads the ones OpenDrop may open. */
    fun refresh() {
        val m = manager ?: return
        val p = probe ?: return
        scope.launch(Dispatchers.IO) {
            val entries = try {
                m.deviceList.values.map { device ->
                    val output = runCatching { UsbAudioOutputs.find(audio, device.productName) }.getOrNull()
                    UsbEntry(device.deviceName, p.read(device), m.hasPermission(device), output)
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't list USB devices", e)
                emptyList()
            }
            _devices.update { entries.sortedWith(compareByDescending<UsbEntry> { it.likelyMoondrop }.thenBy { it.title ?: "" }) }
            val keys = entries.map { it.key }.toSet()
            _dac.update { it.filterKeys(keys::contains) }
        }
    }

    /**
     * Developer options: reads a Comtrue DAC's settings (filter, gain, LED,
     * volume) into [dac]. Does nothing for other families.
     */
    fun refreshDac(key: String) {
        val m = manager ?: return
        val c = console ?: return
        val device = m.deviceList[key] ?: return
        val reads = UsbProbes.readsFor(familyOf(key)).takeIf { familyOf(key) == UsbFamily.COMTRUE } ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                io.withLock {
                    reads.forEach { read ->
                        val result = runCatching { c.query(device, read) }.getOrNull()
                        if (result is UsbConsole.Result.Reply) remember(key, result.bytes)
                    }
                }
            }
        }
    }

    private fun remember(key: String, reply: ByteArray) =
        _dac.update { it + (key to (it[key] ?: ComtrueState()).with(reply)) }

    /**
     * Developer console: sends one [UsbProbes] read to the device [key] names,
     * if the family it was identified as offers it. One at a time.
     */
    fun probe(key: String, read: UsbRead): Boolean {
        val family = familyOf(key) ?: return false
        if (read !in UsbProbes.readsFor(family)) return false
        return run(UsbProbeResult(key, read, ProbeStatus.WAITING)) { c, device -> c.query(device, read) }
    }

    /**
     * Developer console: sets one of [UsbProbes.writes] (filter, LED) and
     * reads it back, if the device's family offers it. One at a time.
     */
    fun write(key: String, write: UsbWrite, value: Int): Boolean {
        val family = familyOf(key) ?: return false
        if (write !in UsbProbes.writesFor(family) || value !in write.values.indices) return false
        val pending = UsbProbeResult(key, write.readBack, ProbeStatus.WAITING, write = write, value = value)
        return run(pending) { c, device -> c.write(device, write, value) }
    }

    private fun familyOf(key: String) = _devices.value.firstOrNull { it.key == key }?.family

    /** Publishes [pending], runs [block] off the main thread, then publishes its result. */
    private fun run(pending: UsbProbeResult, block: (UsbConsole, UsbDevice) -> UsbConsole.Result): Boolean {
        val m = manager ?: return false
        val c = console ?: return false
        val device = m.deviceList[pending.key] ?: return false
        if (_consoleResult.value?.status == ProbeStatus.WAITING) return false
        _consoleResult.value = pending
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                io.withLock {
                    runCatching { block(c, device) }.getOrElse { UsbConsole.Result.Failed(it.message ?: it.javaClass.simpleName) }
                }
            }
            if (result is UsbConsole.Result.Reply && familyOf(pending.key) == UsbFamily.COMTRUE) {
                remember(pending.key, result.bytes)
            }
            _consoleResult.value = when (result) {
                is UsbConsole.Result.Reply -> pending.copy(status = ProbeStatus.REPLY, reply = result.bytes.toList())
                is UsbConsole.Result.Failed -> pending.copy(status = ProbeStatus.ERROR, message = result.message)
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
