package org.opendrop.app.ui

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
import androidx.compose.ui.unit.dp
import org.opendrop.app.device.Connection
import org.opendrop.app.device.UiState
import org.opendrop.app.ui.components.InfoRow
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.theme.Dimens

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
            item(key = "firmware") { InfoRow("Firmware", state.device.firmwareVersion ?: "–") }
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
