package org.opendrop.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.opendrop.app.R
import org.opendrop.app.device.ProbeStatus
import org.opendrop.app.device.UsbEntry
import org.opendrop.app.device.UsbProbeResult
import org.opendrop.app.ui.components.InfoRow
import org.opendrop.app.ui.components.NavRow
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.protocol.UsbIdentify
import org.opendrop.protocol.UsbProbes
import org.opendrop.protocol.UsbRead
import org.opendrop.protocol.UsbReport
import org.opendrop.protocol.UsbWrite

/**
 * One USB device (roadmap M5): what it reports, and a report to share. With
 * developer options on, also the console's get commands for its family.
 */
@Composable
fun UsbDeviceScreen(
    entry: UsbEntry?,
    developerOptions: Boolean,
    probe: UsbProbeResult?,
    onAllow: (String) -> Unit,
    onProbe: (String, UsbRead) -> Unit,
    onWrite: (String, UsbWrite, Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(entry?.let { it.title ?: stringResource(R.string.usb_device_id, it.info.id) } ?: stringResource(R.string.usb_device), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        ) {
            if (entry == null) {
                Note(stringResource(R.string.usb_unplugged))
                return@Column
            }
            val info = entry.info
            InfoRow(
                stringResource(R.string.info_model),
                info.model?.name ?: "–",
                note = info.model?.let { stringResource(R.string.usb_model_note, it.family.label, it.eqBands) }
                    ?: stringResource(R.string.usb_model_unknown),
            )
            InfoRow(stringResource(R.string.usb_id), info.id)
            InfoRow(stringResource(R.string.usb_manufacturer), info.manufacturer ?: "–")
            InfoRow(stringResource(R.string.usb_product), info.product ?: "–")
            InfoRow(stringResource(R.string.usb_version), info.version ?: "–")
            InfoRow(
                stringResource(R.string.usb_interfaces),
                info.interfaces.size.toString(),
                note = info.interfaces.joinToString(", ") { UsbReport.className(it.interfaceClass) }.ifEmpty { null },
            )

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle(stringResource(R.string.usb_support))
            Note(
                stringResource(R.string.usb_support_note),
            )
            if (!entry.hasPermission) {
                NavRow(
                    title = stringResource(R.string.usb_allow),
                    subtitle = stringResource(R.string.usb_allow_subtitle),
                    onClick = { onAllow(entry.key) },
                )
            }
            NavRow(
                title = stringResource(R.string.usb_share_report),
                subtitle = stringResource(if (entry.hasPermission) R.string.usb_share_full else R.string.usb_share_basic),
                onClick = { shareUsbReport(context, entry) },
            )

            if (developerOptions) {
                val family = UsbIdentify.of(info).family
                val reads = UsbProbes.readsFor(family)
                Spacer(Modifier.height(Dimens.SectionGap))
                SectionTitle(stringResource(R.string.devtools_console))
                Note(stringResource(R.string.devtools_warning))
                when {
                    reads.isEmpty() -> Note(stringResource(R.string.console_none))
                    !entry.hasPermission -> Note(stringResource(R.string.usb_allow_subtitle))
                    else -> {
                        val result = probe?.takeIf { it.key == entry.key }
                        result?.let { UsbProbeCard(it) }
                        reads.forEach { read ->
                            NavRow(
                                title = "${family?.label}: ${read.label}",
                                subtitle = read.wire(),
                                onClick = { onProbe(entry.key, read) },
                            )
                        }
                        val busy = result?.status == ProbeStatus.WAITING
                        UsbProbes.writesFor(family).forEach { write ->
                            // The current value, if the last reply holds it.
                            val current = result?.takeIf { it.status == ProbeStatus.REPLY && it.read == write.readBack }
                                ?.let { write.readBackValue(it.reply.toByteArray()) }
                            WriteRow(write, current, enabled = !busy) { value -> onWrite(entry.key, write, value) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsbProbeCard(result: UsbProbeResult) {
    val write = result.write
    val value = result.value
    val text = when (result.status) {
        ProbeStatus.WAITING -> stringResource(R.string.console_waiting)
        ProbeStatus.REPLY -> {
            val reply = result.reply.toByteArray()
            val described = UsbProbes.describe(result.read, reply)
            if (write == null || value == null) {
                described
            } else {
                val back = write.readBackValue(reply)
                (if (back == value) "Took: reads back $value" else "Didn't take: reads back ${back ?: "nothing"}") +
                    "\n$described"
            }
        }
        else -> result.message ?: stringResource(R.string.console_not_sent)
    }
    if (write != null && value != null) {
        val packet = write.packet(value).joinToString(" ") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF) }
        ConsoleResultCard("Set ${write.label} to $value (${write.values[value]})", "$packet, then ${result.read.wire()}", text)
    } else {
        ConsoleResultCard(result.read.label, result.read.wire(), text)
    }
}

/** A reversible setting: one chip per value, the current one selected when known. */
@Composable
private fun WriteRow(write: UsbWrite, current: Int?, enabled: Boolean, onSet: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = Dimens.Gutter, vertical = 8.dp)) {
        Text("${write.family.label}: set ${write.label}", style = MaterialTheme.typography.bodyLarge)
        Text(
            if (current == null) "Read \"${write.readBack.label}\" to see the current value" else "Now: $current (${write.values.getOrNull(current) ?: "unknown"})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            write.values.forEachIndexed { i, name ->
                FilterChip(
                    selected = i == current,
                    enabled = enabled,
                    onClick = { onSet(i) },
                    label = { Text("$i · $name") },
                )
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

private fun shareUsbReport(context: Context, entry: UsbEntry) {
    val appVersion = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
    val text = UsbReport.build(
        entry.info,
        listOf(
            "App" to appVersion,
            "Phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        ),
    )
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "OpenDrop USB report: ${entry.title ?: "USB device ${entry.info.id}"}")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.usb_share_report)))
}
