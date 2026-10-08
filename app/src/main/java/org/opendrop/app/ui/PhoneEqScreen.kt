package org.opendrop.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import org.opendrop.app.phoneeq.PhoneEq
import org.opendrop.app.phoneeq.PhoneEqState
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.components.SegmentedSelector
import org.opendrop.app.ui.components.SwitchRow
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.app.ui.theme.MonoValue
import org.opendrop.dsp.AutoEq
import org.opendrop.dsp.EqCurve
import org.opendrop.dsp.EqPresetCurve
import org.opendrop.dsp.FilterType
import org.opendrop.dsp.GraphicEq
import org.opendrop.dsp.MAX_HZ
import org.opendrop.dsp.MIN_HZ
import org.opendrop.dsp.ParametricEq
import org.opendrop.dsp.PeqFilter
import org.opendrop.dsp.logSpaced

private const val GRAPH_RANGE_DB = 12.0
private const val MAX_FILTERS = 10
private const val MAX_IMPORT_CHARS = 64_000

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhoneEqScreen(
    state: PhoneEqState,
    failed: Boolean,
    onChange: ((PhoneEqState) -> PhoneEqState) -> Unit,
    onCustom: (EqCurve) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var message by remember { mutableStateOf<String?>(null) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) message = importCurve(readText(context, uri), onCustom)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Phone EQ", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (!PhoneEq.supported) {
                Note("The phone EQ needs Android 9 or newer.")
                return@Column
            }
            SwitchRow(
                title = "Phone EQ",
                subtitle = "Shapes everything the phone plays, on any headphones",
                checked = state.enabled,
                onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
            )
            if (state.enabled && failed) {
                Note("This phone didn't accept the EQ effect. Another EQ app may be holding it; turn that off and try again.")
            }

            Spacer(Modifier.height(16.dp))
            ResponseGraph(state.curve)

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("Presets")
            FlowRow(
                Modifier.padding(horizontal = Dimens.Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqPresetCurve.entries.forEach { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = { onChange { it.copy(preset = preset) } },
                        label = { Text(preset.label) },
                    )
                }
                state.custom?.let {
                    FilterChip(
                        selected = state.preset == null,
                        onClick = { onChange { it.copy(preset = null) } },
                        label = { Text("Custom") },
                    )
                }
            }

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("Preamp")
            PreampRow(state.curve) { preamp -> onCustom(state.curve.withPreamp(preamp)) }

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("Filters")
            val parametric = state.parametric
            if (parametric == null) {
                Note("Imported graphic EQ (${(state.curve as GraphicEq).points.size} points). Pick a preset to edit filters.")
            } else {
                FilterList(parametric, onCustom)
            }

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("AutoEQ")
            Note(
                "Import a ParametricEQ.txt or GraphicEQ.txt from AutoEQ (autoeq.app) for your headphones, " +
                    "or copy one and paste it here.",
            )
            Row(Modifier.padding(horizontal = Dimens.Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { import.launch(arrayOf("text/*")) }) { Text("Import file") }
                OutlinedButton(onClick = { message = importCurve(clipboard.getText()?.text, onCustom) }) { Text("Paste") }
                TextButton(onClick = { share(context, state.curve) }) { Text("Share") }
            }
            message?.let { Note(it) }
        }
    }
}

/** Parses AutoEQ text into the custom curve; returns a message for the user. */
private fun importCurve(text: String?, onCustom: (EqCurve) -> Unit): String {
    val curve = text?.take(MAX_IMPORT_CHARS)?.let(AutoEq::parse)
        ?: return "That isn't an AutoEQ ParametricEQ or GraphicEQ text."
    onCustom(curve)
    return when (curve) {
        is ParametricEq -> "Imported ${curve.filters.size} filters, preamp ${fmt(curve.preamp)} dB."
        is GraphicEq -> "Imported a graphic EQ with ${curve.points.size} points, preamp ${fmt(curve.preamp)} dB."
    }
}

private fun readText(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
        val buffer = CharArray(MAX_IMPORT_CHARS)
        val n = reader.read(buffer)
        if (n > 0) String(buffer, 0, n) else null
    }
}.getOrNull()

private fun share(context: Context, curve: EqCurve) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "OpenDrop phone EQ")
        .putExtra(Intent.EXTRA_TEXT, AutoEq.write(curve))
    context.startActivity(Intent.createChooser(send, "Share EQ"))
}

private fun EqCurve.withPreamp(preamp: Double): EqCurve = when (this) {
    is ParametricEq -> copy(preamp = preamp)
    is GraphicEq -> copy(preamp = preamp)
}

private fun fmt(value: Double, decimals: Int = 1) = String.format(Locale.ROOT, "%+.${decimals}f", value)

private fun hzLabel(hz: Double): String =
    if (hz >= 1_000) String.format(Locale.ROOT, "%.1f kHz", hz / 1_000) else String.format(Locale.ROOT, "%.0f Hz", hz)

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 8.dp),
    )
}

/** The curve on a log-frequency axis, ±[GRAPH_RANGE_DB], with grid lines at 100 Hz, 1 kHz, 10 kHz and every 6 dB. */
@Composable
private fun ResponseGraph(curve: EqCurve) {
    val colors = MaterialTheme.colorScheme
    val points = remember(curve) { logSpaced(MIN_HZ, MAX_HZ, 160).map { it to curve.curveDb(it) } }
    val summary = remember(curve) {
        val peak = points.maxBy { it.second }
        val dip = points.minBy { it.second }
        "EQ curve. Largest boost ${fmt(peak.second)} dB at ${hzLabel(peak.first)}, " +
            "largest cut ${fmt(dip.second)} dB at ${hzLabel(dip.first)}."
    }
    Column(Modifier.padding(horizontal = Dimens.Gutter)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = summary },
        ) {
            val span = ln(MAX_HZ) - ln(MIN_HZ)
            fun x(hz: Double) = ((ln(hz) - ln(MIN_HZ)) / span * size.width).toFloat()
            fun y(db: Double) = (size.height / 2 - db.coerceIn(-GRAPH_RANGE_DB, GRAPH_RANGE_DB) / GRAPH_RANGE_DB * size.height / 2).toFloat()
            val grid = colors.outlineVariant
            listOf(100.0, 1_000.0, 10_000.0).forEach { hz ->
                drawLine(grid, Offset(x(hz), 0f), Offset(x(hz), size.height), strokeWidth = 1.dp.toPx())
            }
            listOf(-6.0, 6.0).forEach { db ->
                drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), strokeWidth = 1.dp.toPx())
            }
            drawLine(colors.outline, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
            val path = Path()
            points.forEachIndexed { i, (hz, db) ->
                if (i == 0) path.moveTo(x(hz), y(db)) else path.lineTo(x(hz), y(db))
            }
            drawPath(
                path,
                colors.primary,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("20 Hz", "100", "1 kHz", "10 kHz", "±${GRAPH_RANGE_DB.toInt()} dB").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PreampRow(curve: EqCurve, onPreamp: (Double) -> Unit) {
    Column(Modifier.padding(horizontal = Dimens.Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${fmt(curve.preamp)} dB", style = MonoValue, modifier = Modifier.weight(1f))
            TextButton(onClick = { onPreamp(curve.safePreamp()) }) { Text("Auto") }
        }
        Slider(
            value = curve.preamp.toFloat(),
            onValueChange = { onPreamp(Math.round(it * 2) / 2.0) },
            valueRange = -20f..0f,
        )
        if (curve.preamp + curve.peakDb() > 0.5) {
            Text(
                "Boosts reach ${fmt(curve.preamp + curve.peakDb())} dB; the limiter will catch peaks. Auto avoids that.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterList(eq: ParametricEq, onCustom: (EqCurve) -> Unit) {
    var expanded by rememberSaveable { mutableIntStateOf(-1) }
    fun replace(i: Int, filter: PeqFilter) = onCustom(eq.copy(filters = eq.filters.toMutableList().also { it[i] = filter }))

    if (eq.filters.isEmpty()) Note("No filters: the curve is flat.")
    eq.filters.forEachIndexed { i, filter ->
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = if (expanded == i) -1 else i }
                .padding(horizontal = Dimens.Gutter, vertical = 10.dp),
        ) {
            Text(
                buildString {
                    append("${filter.type.label} · ${hzLabel(filter.frequency)}")
                    if (filter.type.hasGain) append(" · ${fmt(filter.gain)} dB")
                    append(" · Q ${String.format(Locale.ROOT, "%.2f", filter.q)}")
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (expanded == i) {
            Column(Modifier.padding(horizontal = Dimens.Gutter)) {
                SegmentedSelector(
                    options = FilterType.entries,
                    selected = filter.type,
                    label = { it.code },
                    onSelect = { type -> replace(i, filter.copy(type = type, gain = if (type.hasGain) filter.gain else 0.0)) },
                )
                LabeledSlider("Frequency", hzLabel(filter.frequency), logPosition(filter.frequency, MIN_HZ, MAX_HZ)) {
                    replace(i, filter.copy(frequency = Math.round(fromLogPosition(it, MIN_HZ, MAX_HZ)).toDouble()))
                }
                if (filter.type.hasGain) {
                    LabeledSlider("Gain", "${fmt(filter.gain)} dB", ((filter.gain + 12) / 24).toFloat()) {
                        replace(i, filter.copy(gain = Math.round((it * 24 - 12) * 2) / 2.0))
                    }
                }
                LabeledSlider("Q", String.format(Locale.ROOT, "%.2f", filter.q), logPosition(filter.q, 0.1, 10.0)) {
                    replace(i, filter.copy(q = Math.round(fromLogPosition(it, 0.1, 10.0) * 100) / 100.0))
                }
                TextButton(onClick = {
                    expanded = -1
                    onCustom(eq.copy(filters = eq.filters.filterIndexed { j, _ -> j != i }))
                }) { Text("Remove filter") }
            }
        }
    }
    if (eq.filters.size < MAX_FILTERS) {
        TextButton(
            onClick = {
                expanded = eq.filters.size
                onCustom(eq.copy(filters = eq.filters + PeqFilter()))
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("Add filter") }
    }
}

@Composable
private fun LabeledSlider(title: String, value: String, position: Float, onPosition: (Float) -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MonoValue, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(value = position.coerceIn(0f, 1f), onValueChange = onPosition)
}

private fun logPosition(value: Double, min: Double, max: Double): Float =
    ((ln(value) - ln(min)) / (ln(max) - ln(min))).toFloat()

private fun fromLogPosition(position: Float, min: Double, max: Double): Double = min * (max / min).pow(position.toDouble())
