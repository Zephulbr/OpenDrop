package org.opendrop.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.opendrop.app.R
import org.opendrop.app.device.Connection
import org.opendrop.app.device.ProbeResult
import org.opendrop.app.device.ProbeStatus
import org.opendrop.app.device.TrialStatus
import org.opendrop.app.device.UiState
import org.opendrop.app.device.UserEqTrial
import org.opendrop.app.ui.components.NavRow
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.protocol.DevProbes
import org.opendrop.protocol.DevRead
import org.opendrop.protocol.EqPreset
import org.opendrop.protocol.GaiaFeature
import org.opendrop.protocol.toHex

/**
 * Developer options → protocol console: read-only [DevProbes] queries for the
 * features the connected device advertised, one at a time, plus the one write
 * test (preset 63 for 15 s, then back). Probe labels and results stay in
 * English on purpose, like the packet log and the reports.
 */
@Composable
fun ProtocolConsoleScreen(
    state: UiState,
    showSerials: Boolean,
    onProbe: (DevRead) -> Unit,
    onTryUserEq: () -> Unit,
    onBack: () -> Unit,
) {
    val device = state.device
    val connected = state.connection == Connection.Connected
    val groups = if (connected && device.featuresKnown) {
        DevProbes.readsFor(device.features.keys, showIdentifying = showSerials).groupBy { it.feature }
    } else {
        emptyMap()
    }
    val trialRunning = state.userEqTrial?.status == TrialStatus.RUNNING
    val canTry = connected && device.canTryUserEq && !trialRunning
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
                item(key = "result") { ProbeCard(result, showSerials) }
            }
            if (canTry || state.userEqTrial != null) {
                item(key = "gapTrial") { Spacer(Modifier.height(Dimens.SectionGap)) }
                item(key = "titleTrial") { SectionTitle(stringResource(R.string.console_write_test)) }
                item(key = "noteTrial") { Note(stringResource(R.string.console_write_test_note)) }
                state.userEqTrial?.let { trial ->
                    item(key = "trial") { TrialCard(trial) }
                }
                if (canTry) {
                    item(key = "tryUserEq") {
                        NavRow(
                            title = "Try EQ preset ${EqPreset.USER_ID} for 15 s",
                            subtitle = trialWire(device.namedEqPreset),
                            onClick = onTryUserEq,
                        )
                    }
                }
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
private fun ProbeCard(result: ProbeResult, showSerials: Boolean) {
    val payload = result.payload.toByteArray()
    val text = when (result.status) {
        ProbeStatus.WAITING -> stringResource(R.string.console_waiting)
        ProbeStatus.REPLY -> DevProbes.describe(result.read, payload, reveal = showSerials)
        ProbeStatus.ERROR -> stringResource(R.string.console_error, payload.toHex().ifEmpty { "–" })
        ProbeStatus.TIMEOUT -> stringResource(R.string.console_timeout)
        ProbeStatus.NOT_SENT -> stringResource(R.string.console_not_sent)
    }
    ConsoleResultCard(result.read.label, result.read.wire(), text)
}

@Composable
private fun TrialCard(trial: UserEqTrial) {
    val back = "${trial.previous.label} (${trial.previous.id})"
    val status = when (trial.status) {
        TrialStatus.RUNNING -> "Running, back to $back after 15 s"
        TrialStatus.RESTORED -> "Done, back on $back"
        TrialStatus.NOT_SENT -> "Not sent: the link dropped or the device no longer allows it"
        TrialStatus.NOT_RESTORED -> "Link dropped before switching back; the earbuds may still be on preset ${EqPreset.USER_ID}. Pick a preset by hand."
    }
    fun hex(payloads: List<List<Byte>>) =
        payloads.joinToString(", ") { it.toByteArray().toHex().ifEmpty { "(empty)" } }.ifEmpty { "none" }
    val text = listOf(
        status,
        "set responses: ${hex(trial.responses)}",
        "errors: ${hex(trial.errors)}",
        "preset notifications: " +
            trial.notified.joinToString(", ") { String.format(Locale.ROOT, "%02x", it) }.ifEmpty { "none" },
    ).joinToString("\n")
    ConsoleResultCard("EQ preset ${EqPreset.USER_ID} trial", trialWire(trial.previous), text)
}

/** The last result, selectable and with a button that copies it as plain text. Also used for USB. */
@Composable
internal fun ConsoleResultCard(label: String, wire: String, text: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.console_last_reply),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString("$label\n$wire\n$text")) }) {
                    Text(stringResource(R.string.console_copy))
                }
            }
            SelectionContainer(Modifier.padding(end = 8.dp)) {
                Column {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(wire, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** "f5 cmd 0x05 · 00 04", the frame as the packet log shows it. */
private fun DevRead.wire(): String {
    val head = String.format(Locale.ROOT, "f%d cmd 0x%02x", feature, cmd)
    return if (payload.isEmpty()) head else "$head · " + payload.joinToString(" ") { String.format(Locale.ROOT, "%02x", it) }
}

/** "f5 cmd 0x03 · 3f, then 01": select preset 63, then the one to go back to. */
private fun trialWire(previous: EqPreset?): String {
    val select = String.format(Locale.ROOT, "f%d cmd 0x03 · %02x", GaiaFeature.MUSIC_PROCESSING, EqPreset.USER_ID)
    return if (previous == null) select else select + String.format(Locale.ROOT, ", then %02x", previous.id)
}
