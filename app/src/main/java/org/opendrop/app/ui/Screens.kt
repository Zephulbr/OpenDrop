package org.opendrop.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opendrop.app.device.Connection
import org.opendrop.app.device.DeviceViewModel
import org.opendrop.app.device.PairedDevice
import org.opendrop.app.device.UiState
import org.opendrop.protocol.EqPreset

@Composable
fun OpenDropApp(viewModel: DeviceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("OpenDrop", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.padding(4.dp))
            when {
                !state.hasPermission -> PermissionScreen(onResult = viewModel::refresh)
                !state.bluetoothOn -> Message("Turn on Bluetooth, then come back.")
                state.connection == Connection.Disconnected || state.connection is Connection.Failed ->
                    DeviceListScreen(state, onConnect = viewModel::connect)
                else -> DeviceScreen(
                    state = state,
                    onEq = viewModel::setEq,
                    onVolume = viewModel::setVolume,
                    onDisconnect = viewModel::disconnect,
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(onResult: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("OpenDrop needs permission to talk to your paired earbuds. It doesn't scan or use your location.")
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                onResult()
            }
        }) { Text("Allow Bluetooth access") }
    }
}

@Composable
private fun DeviceListScreen(state: UiState, onConnect: (PairedDevice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (state.connection as? Connection.Failed)?.let {
            Text(
                "Couldn't connect to ${state.selected?.name ?: "the earbuds"}: ${it.message}. " +
                    "Make sure they're out of the case and connected, and the MOONDROP Link app is closed.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.devices.isEmpty()) {
            Message("No paired devices. Pair your Space Travel in Android's Bluetooth settings first.")
        } else {
            Text("Paired devices", style = MaterialTheme.typography.titleMedium)
            DeviceList(state.devices, onConnect)
        }
    }
}

@Composable
private fun DeviceList(devices: List<PairedDevice>, onConnect: (PairedDevice) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices, key = { it.address }) { device ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onConnect(device) },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (device.likelyMoondrop) "Tap to connect" else "Not recognised as Moondrop",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceScreen(
    state: UiState,
    onEq: (EqPreset) -> Unit,
    onVolume: (Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(state, onDisconnect)
        if (state.connection == Connection.Connected) {
            VolumeCard(state, onVolume)
            if (state.device.supportsEq) EqCard(state.device.eqPresetId, onEq)
        }
        PacketLog(state.log)
    }
}

@Composable
private fun StatusCard(state: UiState, onDisconnect: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.selected?.name ?: "Earbuds", style = MaterialTheme.typography.titleLarge)
            if (state.connection == Connection.Connecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting…")
                }
            } else {
                Text("Battery: ${state.battery?.let { "$it %" } ?: "unknown"}")
                Text("Firmware: ${state.device.firmwareVersion ?: "…"}")
            }
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        }
    }
}

@Composable
private fun VolumeCard(state: UiState, onVolume: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Volume", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = state.volume.toFloat(),
                onValueChange = { onVolume(it.toInt()) },
                valueRange = 0f..state.maxVolume.toFloat(),
                steps = (state.maxVolume - 1).coerceAtLeast(0),
            )
        }
    }
}

@Composable
private fun EqCard(currentId: Int?, onEq: (EqPreset) -> Unit) {
    var pending by remember { mutableStateOf<EqPreset?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "EQ preset",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            EqPreset.entries.forEach { preset ->
                val selected = preset.id == currentId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) {
                            if (!selected) pending = preset
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(preset.label)
                }
            }
            if (currentId != null && EqPreset.of(currentId) == null) {
                Text("Unknown preset ($currentId)", Modifier.padding(horizontal = 16.dp))
            }
        }
    }
    pending?.let { preset ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Switch to ${preset.label}?") },
            text = { Text("Switching may cause a brief pop. Lower the volume first.") },
            confirmButton = {
                TextButton(onClick = { onEq(preset); pending = null }) { Text("Switch") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PacketLog(lines: List<String>) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = { open = !open }) { Text(if (open) "Hide packet log" else "Show packet log") }
            if (open) {
                if (lines.isEmpty()) Text("No packets yet.")
                lines.takeLast(100).forEach {
                    Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}
