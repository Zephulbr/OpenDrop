package org.opendrop.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.opendrop.app.device.Connection
import org.opendrop.app.device.PairedDevice
import org.opendrop.app.device.UiState
import org.opendrop.app.ui.components.NavRow
import org.opendrop.app.ui.components.RollingNumber
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.components.SegmentedSelector
import org.opendrop.app.ui.components.StatusDot
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.app.ui.theme.LocalHaptics
import org.opendrop.app.ui.theme.LocalMotion
import org.opendrop.app.ui.theme.MonoValue
import org.opendrop.protocol.EqPreset

/** If the earbuds don't confirm a preset change in this time, the selector springs back. */
private const val EQ_CONFIRM_TIMEOUT_MS = 2_000L

private enum class Phase { Permission, BluetoothOff, Picker, Device }

private val UiState.phase: Phase
    get() = when {
        !hasPermission -> Phase.Permission
        !bluetoothOn -> Phase.BluetoothOff
        connection == Connection.Disconnected || connection is Connection.Failed -> Phase.Picker
        else -> Phase.Device
    }

@Composable
fun HomeScreen(
    state: UiState,
    onPermissionResult: () -> Unit,
    onConnect: (PairedDevice) -> Unit,
    onDisconnect: () -> Unit,
    onEq: (EqPreset) -> Unit,
    onVolume: (Int) -> Unit,
    onOpenDeviceInfo: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    ConnectionHaptics(state.connection)
    val listState = rememberLazyListState()
    val phase = state.phase
    val motion = LocalMotion.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val name = if (phase == Phase.Device) state.selected?.name ?: "Earbuds" else "OpenDrop"
    val battery = state.battery.takeIf { phase == Phase.Device }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + Dimens.TopBar,
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                HeroSection(state, phase, name, battery)
            }
            item(key = "heroGap") { Spacer(Modifier.height(Dimens.SectionGap)) }

            when (phase) {
                Phase.Permission -> item(key = "permission") {
                    PermissionSection(onPermissionResult, Modifier.animateItem())
                }
                Phase.BluetoothOff -> item(key = "bluetoothOff") {
                    Message("Turn on Bluetooth, then come back.", Modifier.animateItem())
                }
                Phase.Picker -> {
                    (state.connection as? Connection.Failed)?.let { failed ->
                        item(key = "error") {
                            ErrorNote(state.selected?.name, failed.message, Modifier.animateItem())
                        }
                    }
                    if (state.devices.isEmpty()) {
                        item(key = "noDevices") {
                            Message(
                                "No paired devices. Pair your Space Travel in Android's Bluetooth settings first.",
                                Modifier.animateItem(),
                            )
                        }
                    } else {
                        item(key = "pairedTitle") { SectionTitle("Paired devices", Modifier.animateItem()) }
                        items(state.devices, key = { it.address }) { device ->
                            NavRow(
                                title = device.name,
                                subtitle = if (device.likelyMoondrop) "Tap to connect" else "Not recognised as Moondrop",
                                onClick = { onConnect(device) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = motion.effectsDefault(),
                                    placementSpec = motion.spatialDefault(),
                                    fadeOutSpec = motion.effectsFast(),
                                ),
                            )
                        }
                    }
                }
                Phase.Device -> {
                    if (state.connection == Connection.Connected && state.device.supportsEq) {
                        item(key = "eq") { EqSection(state.device.eqPresetId, onEq, Modifier.animateItem()) }
                        item(key = "eqGap") { Spacer(Modifier.height(Dimens.SectionGap)) }
                    }
                    item(key = "volume") {
                        VolumeSection(state.volume, state.maxVolume, onVolume, Modifier.animateItem())
                    }
                    item(key = "volumeGap") { Spacer(Modifier.height(Dimens.SectionGap)) }
                    item(key = "deviceInfo") {
                        NavRow(
                            title = "Device info",
                            value = state.device.firmwareVersion,
                            onClick = onOpenDeviceInfo,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "disconnect") {
                        DisconnectButton(state.connection, onDisconnect, Modifier.animateItem())
                    }
                }
            }
        }

        CollapsingTopBar(listState, name, battery, onOpenAppearance)
    }
}

@Composable
private fun ConnectionHaptics(connection: Connection) {
    val haptics = LocalHaptics.current
    var last by remember { mutableStateOf(connection) }
    LaunchedEffect(connection) {
        val previous = last
        last = connection
        when {
            connection == Connection.Connected && previous != Connection.Connected -> haptics.confirm()
            connection is Connection.Failed && previous !is Connection.Failed -> haptics.reject()
        }
    }
}

@Composable
private fun HeroSection(state: UiState, phase: Phase, name: String, battery: Int?) {
    val mode = when {
        phase != Phase.Device -> HeroMode.Disconnected
        state.connection == Connection.Connected -> HeroMode.Connected
        else -> HeroMode.Connecting
    }
    val status = statusLabel(state.connection, phase)
    val description = buildString {
        append(name).append(", ").append(status.lowercase())
        battery?.let { append(", battery ").append(it).append(" percent") }
    }
    Column(Modifier.fillMaxWidth()) {
        EarbudsHero(mode, battery, state.device.eqPresetId, description)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Gutter),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(statusColor(state.connection, phase))
                    Spacer(Modifier.width(8.dp))
                    StatusText(status)
                }
            }
            if (battery != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(battery, MaterialTheme.typography.displaySmall)
                    Text(
                        "%",
                        style = MonoValue,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

private fun statusLabel(connection: Connection, phase: Phase): String = when {
    phase == Phase.Permission || phase == Phase.BluetoothOff -> "Not connected"
    connection == Connection.Connected -> "Connected"
    connection == Connection.Connecting -> "Connecting…"
    connection == Connection.Reconnecting -> "Reconnecting…"
    connection is Connection.Failed -> "Couldn't connect"
    else -> "Not connected"
}

@Composable
private fun statusColor(connection: Connection, phase: Phase) = when {
    phase == Phase.Device && connection == Connection.Connected -> MaterialTheme.colorScheme.primary
    connection is Connection.Failed -> MaterialTheme.colorScheme.error
    phase == Phase.Device -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StatusText(text: String) {
    val motion = LocalMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(motion.effectsDefault()) togetherWith fadeOut(motion.effectsFast()) },
        label = "status",
    ) { label ->
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Overlays the list. Its background, divider and compact title fade in as
 * the hero scrolls away; scroll is read in draw/layer lambdas only.
 */
@Composable
private fun CollapsingTopBar(listState: LazyListState, name: String, battery: Int?, onOpenAppearance: () -> Unit) {
    val collapseDistance = with(LocalDensity.current) { 140.dp.toPx() }
    val collapse by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
            }
        }
    }
    val background = MaterialTheme.colorScheme.background
    val divider = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(background, alpha = collapse)
                drawLine(
                    divider,
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                    alpha = collapse,
                )
            }
            .statusBarsPadding()
            .height(Dimens.TopBar),
    ) {
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = Dimens.Gutter, end = 64.dp)
                .graphicsLayer { alpha = collapse },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (battery != null) {
                Spacer(Modifier.width(12.dp))
                Text("$battery %", style = MonoValue, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(
            onClick = onOpenAppearance,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Appearance settings")
        }
    }
}

@Composable
private fun EqSection(currentId: Int?, onEq: (EqPreset) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    // Shown right away; cleared when the earbuds report back, or after a timeout.
    var pending by remember { mutableStateOf<EqPreset?>(null) }
    LaunchedEffect(currentId) { pending = null }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(EQ_CONFIRM_TIMEOUT_MS)
            pending = null
        }
    }
    val shown = pending ?: currentId?.let(EqPreset::of)

    Column(modifier.fillMaxWidth()) {
        SectionTitle("EQ")
        SegmentedSelector(
            options = EqPreset.entries,
            selected = shown,
            label = { it.label },
            onSelect = { preset ->
                haptics.segment()
                pending = preset
                onEq(preset)
            },
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
        )
        Spacer(Modifier.height(8.dp))
        val hint = if (currentId != null && EqPreset.of(currentId) == null) {
            "Unknown preset ($currentId). Switching may pop briefly."
        } else {
            "Switching may pop briefly. Lower the volume first."
        }
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSection(volume: Int, maxVolume: Int, onVolume: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val thumbScale by animateFloatAsState(
        if (dragged || pressed) 1f else 0.72f,
        motion.spatialFast(),
        label = "thumb",
    )

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = Dimens.Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("Volume", Modifier.weight(1f))
            RollingNumber(volume, MonoValue, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        Slider(
            value = volume.toFloat(),
            onValueChange = {
                val level = it.roundToInt()
                if (level != volume) {
                    haptics.tick()
                    onVolume(level)
                }
            },
            valueRange = 0f..maxVolume.coerceAtLeast(1).toFloat(),
            steps = (maxVolume - 1).coerceAtLeast(0),
            interactionSource = interaction,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.surfaceContainerHighest,
                activeTickColor = colors.onPrimary.copy(alpha = 0.5f),
                inactiveTickColor = colors.onSurfaceVariant.copy(alpha = 0.4f),
            ),
            thumb = {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = thumbScale
                                scaleY = thumbScale
                            }
                            .clip(CircleShape)
                            .background(colors.primary),
                    )
                }
            },
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
        )
    }
}

@Composable
private fun DisconnectButton(connection: Connection, onDisconnect: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onDisconnect) {
            Text(if (connection == Connection.Connected) "Disconnect" else "Cancel")
        }
    }
}

@Composable
private fun PermissionSection(onResult: () -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    Column(
        modifier.padding(horizontal = Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "OpenDrop needs permission to talk to your paired earbuds. It doesn't scan or use your location.",
            style = MaterialTheme.typography.bodyLarge,
        )
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
private fun ErrorNote(deviceName: String?, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Gutter)
            .padding(bottom = Dimens.SectionGap)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Couldn't connect to ${deviceName ?: "the earbuds"}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            "$message. Make sure they're out of the case and connected, and the MOONDROP Link app is closed.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.padding(horizontal = Dimens.Gutter),
    )
}
