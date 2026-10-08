package org.opendrop.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.opendrop.app.device.Connection
import org.opendrop.app.device.UiState
import org.opendrop.app.ui.components.InfoRow
import org.opendrop.app.ui.components.NavRow
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.protocol.Chip
import org.opendrop.protocol.DeviceReport
import org.opendrop.protocol.GaiaDeviceState
import org.opendrop.protocol.GaiaFeature
import org.opendrop.protocol.Support
import org.opendrop.protocol.capabilities

private const val LOG_LINES_SHOWN = 100

@Composable
fun DeviceInfoScreen(state: UiState, onBack: () -> Unit) {
    val lines = state.log.takeLast(LOG_LINES_SHOWN)
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Device info", onBack)
        LazyColumn(
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "name") { InfoRow("Name", state.selected?.name ?: "–") }
            item(key = "address") { InfoRow("Address", state.selected?.address ?: "–") }
            item(key = "model") {
                val device = state.device
                InfoRow(
                    "Model",
                    device.model?.name ?: device.variantName ?: "–",
                    note = device.model?.let { "${it.chip.label()} chip, controlled over GAIA" }
                        ?: device.variantName?.let { "Not in OpenDrop's model list, so it's read-only." },
                )
            }
            item(key = "firmware") { InfoRow("Firmware", state.device.firmwareVersion ?: "–") }
            if (state.device.featuresKnown) {
                item(key = "features") {
                    InfoRow(
                        "GAIA features",
                        state.device.features.keys.size.toString(),
                        note = state.device.features.keys.sorted().joinToString(", ") { GaiaFeature.name(it) },
                    )
                }
            }
            waitingOnCapture(state.device)?.let { waiting ->
                item(key = "needsCapture") {
                    InfoRow(
                        "Not supported yet",
                        waiting,
                        note = "The device has these, but OpenDrop needs a capture before it sends their commands.",
                    )
                }
            }
            item(key = "battery") {
                InfoRow(
                    "Battery",
                    state.battery?.let { "$it %" } ?: "–",
                    note = "One level for both earbuds, in 10 % steps. The case isn't reported.",
                )
            }
            item(key = "link") {
                InfoRow(
                    "Control link",
                    when (state.connection) {
                        Connection.Connected -> "Connected"
                        Connection.Connecting -> "Connecting"
                        Connection.Reconnecting -> "Reconnecting"
                        is Connection.Failed -> "Failed"
                        Connection.Disconnected -> "Off"
                    },
                )
            }
            item(key = "eqCurve") {
                InfoRow(
                    "EQ curve",
                    "Illustrative",
                    note = "The curve on the home screen shows each preset's general shape. It isn't measured.",
                )
            }
            if (state.device.featuresKnown) {
                item(key = "report") {
                    val context = LocalContext.current
                    NavRow(
                        title = "Share device report",
                        subtitle = "Model, features and packet log, to help add support for your device",
                        onClick = { shareReport(context, state) },
                    )
                }
            }
            item(key = "developerGap") { Spacer(Modifier.height(Dimens.SectionGap)) }
            item(key = "developer") { SectionTitle("Developer · packet log") }
            if (lines.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No packets yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.Gutter),
                    )
                }
            }
            items(lines) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.Gutter, vertical = 2.dp),
                )
            }
        }
    }
}

private fun Chip.label(): String = when (this) {
    Chip.BLUETRUM -> "Bluetrum"
    Chip.QUALCOMM -> "Qualcomm"
    Chip.JIELI -> "Jieli"
    Chip.AIROHA -> "Airoha"
}

/** Capabilities the device has that wait on a capture, as a readable list; null if none. */
internal fun waitingOnCapture(device: GaiaDeviceState): String? =
    device.capabilities.filterValues { it == Support.NEEDS_CAPTURE }.keys
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { it.label }

/** Opens the share sheet with a plain-text report. Nothing is sent unless the user picks a target. */
private fun shareReport(context: Context, state: UiState) {
    val appVersion = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
    val report = DeviceReport.build(
        state.device,
        context = listOf(
            "OpenDrop" to appVersion,
            "Phone" to "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
            "Bluetooth name" to (state.selected?.name ?: "?"),
        ),
        log = state.log,
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "OpenDrop device report: ${state.device.variantName ?: state.selected?.name}")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(send, "Share device report"))
}
