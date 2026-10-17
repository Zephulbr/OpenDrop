package org.opendrop.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.opendrop.app.R
import org.opendrop.app.device.Connection
import org.opendrop.app.device.ProbeResult
import org.opendrop.app.device.ProbeStatus
import org.opendrop.app.device.UiState
import org.opendrop.app.ui.components.NavRow
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.protocol.DevProbes
import org.opendrop.protocol.DevRead
import org.opendrop.protocol.GaiaFeature
import org.opendrop.protocol.toHex

/**
 * Developer options → protocol console: read-only [DevProbes] queries for the
 * features the connected device advertised, one at a time. Probe labels stay
 * in English on purpose, like the packet log and the reports.
 */
@Composable
fun ProtocolConsoleScreen(state: UiState, showSerials: Boolean, onProbe: (DevRead) -> Unit, onBack: () -> Unit) {
    val device = state.device
    val connected = state.connection == Connection.Connected
    val groups = if (connected && device.featuresKnown) {
        DevProbes.readsFor(device.features.keys, showIdentifying = showSerials).groupBy { it.feature }
    } else {
        emptyMap()
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.devtools_console), onBack)
        LazyColumn(
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "warning") { Note(stringResource(R.string.devtools_warning)) }
            when {
                !connected -> item(key = "offline") { Note(stringResource(R.string.console_not_connected)) }
                !device.featuresKnown -> item(key = "features") { Note(stringResource(R.string.console_waiting_features)) }
                groups.isEmpty() -> item(key = "none") { Note(stringResource(R.string.console_none)) }
            }
            state.probe?.let { result ->
                item(key = "result") { ResultCard(result, showSerials) }
            }
            groups.forEach { (feature, reads) ->
                item(key = "gap$feature") { Spacer(Modifier.height(Dimens.SectionGap)) }
                item(key = "title$feature") { SectionTitle("${GaiaFeature.name(feature)} ($feature)") }
                items(reads, key = { it.id }) { read ->
                    NavRow(title = read.label, subtitle = read.wire(), onClick = { onProbe(read) })
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 8.dp),
    )
}

@Composable
private fun ResultCard(result: ProbeResult, showSerials: Boolean) {
    val payload = result.payload.toByteArray()
    val text = when (result.status) {
        ProbeStatus.WAITING -> stringResource(R.string.console_waiting)
        ProbeStatus.REPLY -> DevProbes.describe(result.read, payload, reveal = showSerials)
        ProbeStatus.ERROR -> stringResource(R.string.console_error, payload.toHex().ifEmpty { "–" })
        ProbeStatus.TIMEOUT -> stringResource(R.string.console_timeout)
        ProbeStatus.NOT_SENT -> stringResource(R.string.console_not_sent)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.console_last_reply), style = MaterialTheme.typography.labelMedium)
            Text(result.read.label, style = MaterialTheme.typography.bodyLarge)
            Text(result.read.wire(), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "f5 cmd 0x05 · 00 04", the frame as the packet log shows it. */
private fun DevRead.wire(): String {
    val head = String.format(Locale.ROOT, "f%d cmd 0x%02x", feature, cmd)
    return if (payload.isEmpty()) head else "$head · " + payload.joinToString(" ") { String.format(Locale.ROOT, "%02x", it) }
}
