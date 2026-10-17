package org.opendrop.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.opendrop.app.R
import org.opendrop.app.device.ProbeStatus
import org.opendrop.app.device.UsbAudioOutputs
import org.opendrop.app.device.UsbEntry
import org.opendrop.app.device.UsbProbeResult
import org.opendrop.app.ui.components.ChoiceRow
import org.opendrop.app.ui.components.InfoRow
import org.opendrop.app.ui.components.NavRow
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.protocol.ComtrueState
import org.opendrop.protocol.UsbFamily
import org.opendrop.protocol.UsbProbes
import org.opendrop.protocol.UsbRead
import org.opendrop.protocol.UsbReport
import org.opendrop.protocol.UsbWrite

/**
 * One USB device (roadmap M5): model, the formats it plays, support and a
 * report to share. With developer options on, a Comtrue DAC also gets its
 * settings (filter, LED; gain and volume shown) and the raw console.
 */
@Composable
fun UsbDeviceScreen(
    entry: UsbEntry?,
    developerOptions: Boolean,
    probe: UsbProbeResult?,
    dac: ComtrueState?,
    onAllow: (String) -> Unit,
    onProbe: (String, UsbRead) -> Unit,
    onWrite: (String, UsbWrite, Int) -> Unit,
    onRefreshDac: (String) -> Unit,
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

            val dacControls = developerOptions && entry.hasPermission && entry.family == UsbFamily.COMTRUE
            if (dacControls) {
                LaunchedEffect(entry.key) { onRefreshDac(entry.key) }
                DacSection(entry.key, dac, probe?.takeIf { it.key == entry.key }, onWrite, onRefreshDac)
            }

            OutputFormatSection(entry)

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle(stringResource(R.string.usb_support))
            Note(stringResource(R.string.usb_support_note))
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

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle(stringResource(R.string.usb_details))
            InfoRow(stringResource(R.string.usb_id), info.id)
            InfoRow(stringResource(R.string.usb_manufacturer), info.manufacturer ?: "–")
            InfoRow(stringResource(R.string.usb_product), info.product ?: "–")
            InfoRow(stringResource(R.string.usb_version), info.version ?: "–")
            InfoRow(
                stringResource(R.string.usb_interfaces),
                info.interfaces.size.toString(),
                note = info.interfaces.joinToString(", ") { UsbReport.className(it.interfaceClass) }.ifEmpty { null },
            )

            if (developerOptions) ConsoleSection(entry, probe?.takeIf { it.key == entry.key }, onProbe)
        }
    }
}

/**
 * Comtrue DAC settings. A tap shows the choice at once; the DAC's read-back
 * confirms it, or the old value comes back with a note.
 */
@Composable
private fun DacSection(
    key: String,
    dac: ComtrueState?,
    last: UsbProbeResult?,
    onWrite: (String, UsbWrite, Int) -> Unit,
    onRefresh: (String) -> Unit,
) {
    val filter = UsbProbes.writes.single { it.id == "comtrue.filter" }
    val led = UsbProbes.writes.single { it.id == "comtrue.led" }
    val busy = last?.status == ProbeStatus.WAITING
    // The tapped value until the DAC answers.
    var pending by remember { mutableStateOf<Pair<UsbWrite, Int>?>(null) }
    LaunchedEffect(dac, last?.status) { if (!busy) pending = null }

    fun shown(write: UsbWrite, current: Int?) = pending?.takeIf { it.first == write }?.second ?: current
    fun set(write: UsbWrite, value: Int) {
        if (busy) return
        pending = write to value
        onWrite(key, write, value)
    }

    Spacer(Modifier.height(Dimens.SectionGap))
    SectionTitle(stringResource(R.string.usb_dac))
    Note(stringResource(R.string.usb_dac_note))

    SubTitle(stringResource(R.string.usb_dac_filter))
    FILTER_NAMES.forEachIndexed { value, name ->
        ChoiceRow(stringResource(name), selected = shown(filter, dac?.filter) == value, onClick = { set(filter, value) }, enabled = !busy)
    }

    SubTitle(stringResource(R.string.usb_dac_led))
    LED_NAMES.forEachIndexed { value, name ->
        ChoiceRow(stringResource(name), selected = shown(led, dac?.led) == value, onClick = { set(led, value) }, enabled = !busy)
    }

    Spacer(Modifier.height(8.dp))
    InfoRow(
        stringResource(R.string.usb_dac_gain),
        dac?.gain?.let { GAIN_NAMES.getOrNull(it)?.let { name -> stringResource(name) } ?: it.toString() } ?: "–",
        note = stringResource(R.string.usb_read_only),
    )
    InfoRow(
        stringResource(R.string.volume),
        dac?.volumePercent?.let { "$it %" } ?: "–",
        note = stringResource(R.string.usb_read_only),
    )

    // A write that the read-back contradicts, or a transfer that failed.
    val problem = when {
        last == null || busy -> null
        last.status == ProbeStatus.ERROR -> stringResource(R.string.usb_dac_failed, last.message ?: "–")
        last.write != null && last.value != null &&
            last.write.readBackValue(last.reply.toByteArray()) != last.value -> stringResource(R.string.usb_dac_not_taken)
        else -> null
    }
    problem?.let { Note(it, error = true) }
    NavRow(title = stringResource(R.string.usb_dac_refresh), onClick = { onRefresh(key) })
}

/** What the DAC declares it plays, and what Android says it can send it. Read-only, no vendor command. */
@Composable
private fun OutputFormatSection(entry: UsbEntry) {
    val formats = entry.formats
    val output = entry.output
    if (formats.isEmpty() && output == null && entry.hasPermission) return
    Spacer(Modifier.height(Dimens.SectionGap))
    SectionTitle(stringResource(R.string.usb_format))
    if (formats.isEmpty()) {
        if (!entry.hasPermission) Note(stringResource(R.string.usb_format_need_access))
    } else {
        SubTitle(stringResource(R.string.usb_format_dac))
        formats.distinctBy { Triple(it.bits, it.raw, it.channels) }.forEach { f ->
            InfoRow(
                stringResource(if (f.raw) R.string.usb_format_raw else R.string.usb_format_pcm, f.bits),
                f.channels?.let { stringResource(R.string.usb_format_channels, it) } ?: "",
                note = f.sampleRates.takeIf { it.isNotEmpty() }?.let(UsbAudioOutputs::rates),
            )
        }
    }
    output?.let { out ->
        SubTitle(stringResource(R.string.usb_format_android))
        InfoRow(
            stringResource(R.string.usb_format_rates),
            "",
            note = out.sampleRates.takeIf { it.isNotEmpty() }?.let(UsbAudioOutputs::rates)
                ?: stringResource(R.string.usb_format_rates_none),
        )
        if (out.encodings.isNotEmpty()) {
            InfoRow(stringResource(R.string.usb_format_depths), "", note = out.encodings.joinToString(", "))
        }
        out.bitPerfect?.let { bitPerfect ->
            InfoRow(
                stringResource(R.string.usb_format_bit_perfect),
                "",
                note = stringResource(if (bitPerfect) R.string.usb_format_supported else R.string.usb_format_unsupported),
            )
        }
    }
    Note(stringResource(R.string.usb_format_note))
}

/** Developer options: the raw get commands and the last reply, copyable. */
@Composable
private fun ConsoleSection(entry: UsbEntry, result: UsbProbeResult?, onProbe: (String, UsbRead) -> Unit) {
    val family = entry.family
    val reads = UsbProbes.readsFor(family)
    Spacer(Modifier.height(Dimens.SectionGap))
    SectionTitle(stringResource(R.string.devtools_console))
    Note(stringResource(R.string.devtools_warning))
    when {
        reads.isEmpty() -> Note(stringResource(R.string.console_none))
        !entry.hasPermission -> Note(stringResource(R.string.usb_allow_subtitle))
        else -> {
            result?.let { UsbProbeCard(it) }
            reads.forEach { read ->
                NavRow(
                    title = "${family?.label}: ${read.label}",
                    subtitle = read.wire(),
                    onClick = { onProbe(entry.key, read) },
                )
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

@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Dimens.Gutter, end = Dimens.Gutter, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 8.dp),
    )
}

/** Wire values 0..n, in [UsbProbes.Comtrue] order. */
private val FILTER_NAMES = listOf(
    R.string.usb_dac_filter_fast_ll,
    R.string.usb_dac_filter_fast_pc,
    R.string.usb_dac_filter_slow_ll,
    R.string.usb_dac_filter_slow_pc,
    R.string.usb_dac_filter_nos,
)
private val LED_NAMES = listOf(R.string.state_on, R.string.usb_dac_led_until_replug, R.string.state_off)
private val GAIN_NAMES = listOf(R.string.usb_dac_gain_low, R.string.usb_dac_gain_high)

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
