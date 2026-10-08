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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opendrop.app.R
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
    val waiting = waitingOnCapture(state.device)
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.device_info), onBack)
        LazyColumn(
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "name") { InfoRow(stringResource(R.string.info_name), state.selected?.name ?: "–") }
            item(key = "address") { InfoRow(stringResource(R.string.info_address), state.selected?.address ?: "–") }
            item(key = "model") {
                val device = state.device
                InfoRow(
                    stringResource(R.string.info_model),
                    device.model?.name ?: device.variantName ?: "–",
                    note = device.model?.let { stringResource(R.string.info_model_note, it.chip.label()) }
                        ?: device.variantName?.let { stringResource(R.string.info_model_unknown) },
                )
            }
            item(key = "firmware") { InfoRow(stringResource(R.string.info_firmware), state.device.firmwareVersion ?: "–") }
            if (state.device.featuresKnown) {
                item(key = "features") {
                    InfoRow(
                        stringResource(R.string.info_gaia_features),
                        state.device.features.keys.size.toString(),
                        note = state.device.features.keys.sorted().joinToString(", ") { GaiaFeature.name(it) },
                    )
                }
            }
            waiting?.let { list ->
                item(key = "needsCapture") {
                    InfoRow(
                        stringResource(R.string.info_not_supported_yet),
                        list,
                        note = stringResource(R.string.info_not_supported_note),
                    )
                }
            }
            item(key = "battery") {
                InfoRow(
                    stringResource(R.string.info_battery),
                    state.battery?.let { "$it %" } ?: "–",
                    note = stringResource(R.string.info_battery_note),
                )
            }
            item(key = "link") {
                InfoRow(
                    stringResource(R.string.info_control_link),
                    stringResource(
                        when (state.connection) {
                            Connection.Connected -> R.string.link_connected
                            Connection.Connecting -> R.string.link_connecting
                            Connection.Reconnecting -> R.string.link_reconnecting
                            is Connection.Failed -> R.string.link_failed
                            Connection.Disconnected -> R.string.link_off
                        },
                    ),
                )
            }
            item(key = "eqCurve") {
                InfoRow(
                    stringResource(R.string.info_eq_curve),
                    stringResource(R.string.info_eq_curve_value),
                    note = stringResource(R.string.info_eq_curve_note),
                )
            }
            if (state.device.featuresKnown) {
                item(key = "report") {
                    val context = LocalContext.current
                    NavRow(
                        title = stringResource(R.string.info_share_report),
                        subtitle = stringResource(R.string.info_share_report_subtitle),
                        onClick = { shareReport(context, state) },
                    )
                }
            }
            item(key = "developerGap") { Spacer(Modifier.height(Dimens.SectionGap)) }
            item(key = "developer") { SectionTitle(stringResource(R.string.info_packet_log)) }
            if (lines.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.info_no_packets),
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
@Composable
internal fun waitingOnCapture(device: GaiaDeviceState): String? {
    val waiting = device.capabilities.filterValues { it == Support.NEEDS_CAPTURE }.keys
    if (waiting.isEmpty()) return null
    val resources = LocalContext.current.resources
    return waiting.joinToString(", ") { resources.getString(it.labelRes()) }
}

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
    context.startActivity(Intent.createChooser(send, context.getString(R.string.info_share_report)))
}
