package org.opendrop.app.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opendrop.protocol.EqPreset
import org.opendrop.protocol.GaiaFrame
import org.opendrop.protocol.SpaceTravelSession
import org.opendrop.protocol.SpaceTravelState

data class PairedDevice(val name: String, val address: String, val likelyMoondrop: Boolean)

sealed interface Connection {
    data object Disconnected : Connection
    data object Connecting : Connection
    data object Connected : Connection
    data object Reconnecting : Connection
    data class Failed(val message: String) : Connection
}

data class UiState(
    val hasPermission: Boolean = false,
    val bluetoothOn: Boolean = true,
    val devices: List<PairedDevice> = emptyList(),
    val selected: PairedDevice? = null,
    val connection: Connection = Connection.Disconnected,
    val device: SpaceTravelState = SpaceTravelState(),
    /** Percent, from the HFP battery report Android already keeps. */
    val battery: Int? = null,
    val volume: Int = 0,
    val maxVolume: Int = 15,
    val log: List<String> = emptyList(),
)

class DeviceViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context get() = getApplication()
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val audio = context.getSystemService(AudioManager::class.java)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The session isn't thread-safe; every call into it runs on this thread. */
    private val sessionThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var link: RfcommLink? = null
    private var session: SpaceTravelSession? = null
    private var readJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                ACTION_BATTERY_LEVEL_CHANGED -> {
                    val device = intent.bluetoothDevice() ?: return
                    if (device.address != _state.value.selected?.address) return
                    val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                    _state.update { it.copy(battery = level.takeIf { l -> l in 0..100 }) }
                }
                ACTION_VOLUME_CHANGED -> refreshVolume()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
            addAction(ACTION_VOLUME_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    /** Re-reads permission, paired devices and volume. Call after permission changes. */
    fun refresh() {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        _state.update {
            it.copy(
                hasPermission = granted,
                bluetoothOn = bluetooth?.isEnabled == true,
                devices = if (granted) pairedDevices() else emptyList(),
            )
        }
        refreshVolume()
    }

    @SuppressLint("MissingPermission")
    private fun pairedDevices(): List<PairedDevice> =
        (bluetooth?.bondedDevices ?: emptySet())
            .map { d ->
                val name = d.name ?: d.address
                val lower = name.lowercase(Locale.ROOT)
                PairedDevice(name, d.address, "moondrop" in lower || "space travel" in lower)
            }
            .sortedWith(compareByDescending<PairedDevice> { it.likelyMoondrop }.thenBy { it.name })

    fun connect(target: PairedDevice) {
        val device = bluetooth?.getRemoteDevice(target.address) ?: return
        disconnect()
        _state.update {
            it.copy(selected = target, connection = Connection.Connecting, device = SpaceTravelState(), log = emptyList())
        }
        readBattery(device)
        readJob = viewModelScope.launch(Dispatchers.IO) { keepConnected(device) }
    }

    /** Connects, and reconnects with backoff when the earbuds drop the link. */
    private suspend fun keepConnected(device: BluetoothDevice) {
        var failures = 0
        var everConnected = false
        while (true) {
            val ended = connectOnce(device)
            if (!currentCoroutineContext().isActive) return // user disconnected
            everConnected = everConnected || ended.connected
            if (!everConnected) {
                // First attempt failed: report it now; the user can tap again.
                _state.update { it.copy(connection = Connection.Failed(ended.reason)) }
                return
            }
            failures = if (ended.lastedMs >= STABLE_CONNECTION_MS) 0 else failures + 1
            if (failures > RETRY_DELAYS_MS.size) {
                _state.update { it.copy(connection = Connection.Failed(ended.reason)) }
                return
            }
            val wait = RETRY_DELAYS_MS[(failures - 1).coerceAtLeast(0)]
            logEvent("${ended.reason}. Reconnecting in ${wait / 1000} s")
            _state.update { it.copy(connection = Connection.Reconnecting, device = SpaceTravelState()) }
            delay(wait)
        }
    }

    private class Ended(val reason: String, val connected: Boolean, val lastedMs: Long)

    /** One connection, from connect to close. Returns why it ended. */
    private suspend fun connectOnce(device: BluetoothDevice): Ended {
        val newLink = RfcommLink(device)
        try {
            newLink.connect()
        } catch (e: Exception) {
            newLink.close()
            return Ended(e.message ?: e.javaClass.simpleName, connected = false, lastedMs = 0)
        }
        val connectedAt = System.currentTimeMillis()
        link = newLink
        val newSession = SpaceTravelSession(
            send = { bytes -> newLink.write(bytes) },
            onState = { s -> _state.update { it.copy(device = s) } },
            onFrame = ::logFrame,
        )
        session = newSession
        _state.update { it.copy(connection = Connection.Connected) }
        logEvent("Connected")

        val buffer = ByteArray(1024)
        val reason = try {
            withContext(sessionThread) { newSession.start() }
            while (true) {
                val n = newLink.read(buffer)
                if (n < 0) break
                val chunk = buffer.copyOf(n)
                withContext(sessionThread) { newSession.onBytes(chunk) }
            }
            "The earbuds closed the connection"
        } catch (e: IOException) {
            e.message ?: "Connection lost"
        } finally {
            newLink.close()
            if (link === newLink) {
                link = null
                session = null
            }
        }
        return Ended(reason, connected = true, lastedMs = System.currentTimeMillis() - connectedAt)
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        link?.close()
        link = null
        session = null
        _state.update { it.copy(connection = Connection.Disconnected) }
    }

    fun setEq(preset: EqPreset) {
        val s = session ?: return
        viewModelScope.launch(sessionThread) { runCatching { s.setEq(preset) }.onFailure(::reportSendError) }
    }

    fun setVolume(value: Int) {
        audio?.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        refreshVolume()
    }

    private fun refreshVolume() {
        val am = audio ?: return
        _state.update {
            it.copy(
                volume = am.getStreamVolume(AudioManager.STREAM_MUSIC),
                maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            )
        }
    }

    /** Android's stored HFP battery level. Hidden API, so this may fail on some versions. */
    private fun readBattery(device: BluetoothDevice) {
        val level = runCatching { device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int }.getOrNull()
        _state.update { it.copy(battery = level?.takeIf { l -> l in 0..100 }) }
    }

    /** A failed write means the link is dead; the read loop notices and reconnects. */
    private fun reportSendError(e: Throwable) = logEvent("Send failed: ${e.message ?: e.javaClass.simpleName}")

    private fun logFrame(incoming: Boolean, frame: GaiaFrame) =
        log("${if (incoming) "RX" else "TX"} $frame")

    private fun logEvent(message: String) = log("-- $message")

    private fun log(text: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
        _state.update { it.copy(log = (it.log + "$time $text").takeLast(LOG_LINES)) }
    }

    override fun onCleared() {
        disconnect()
        runCatching { context.unregisterReceiver(receiver) }
        sessionThread.close()
    }

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        // Hidden-but-stable framework constants.
        private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val LOG_LINES = 200
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 3_000, 10_000)
        /** A connection that lasted this long resets the retry count. */
        private const val STABLE_CONNECTION_MS = 30_000L
    }
}
