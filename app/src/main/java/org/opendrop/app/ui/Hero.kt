package org.opendrop.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import org.opendrop.app.R
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.app.ui.theme.LocalMotion
import org.opendrop.app.ui.theme.MonoValue
import org.opendrop.protocol.EqPreset

/** How the hero should look; see the hero section in DESIGN.md. */
enum class HeroMode { Disconnected, Connecting, Connected }

private const val LOW_BATTERY = 20

/** Curve points per line. */
private const val SAMPLES = 96

/** Level shown from the baseline to the top of the plot. */
private const val RANGE_DB = 11f

/**
 * Where the 0 level sits, as a fraction of the plot height from the top.
 * The presets mostly boost, so there's more room above the line than below.
 */
private const val BASELINE = 0.64f

private fun bump(t: Float, center: Float, width: Float): Float {
    val x = (t - center) / width
    return exp(-x * x)
}

/**
 * Illustrative response shapes, not measurements: t is the position on a log
 * frequency axis (0 = 20 Hz, 1 = 20 kHz), the result a relative level.
 * Replace with measured curves when someone has them.
 */
fun illustrativeCurve(preset: EqPreset, t: Float): Float = when (preset) {
    EqPreset.REFERENCE -> 1.5f * bump(t, 0.05f, 0.22f)
    EqPreset.BASSHEAD -> 9f * bump(t, 0f, 0.30f) + 1.5f * bump(t, 0.88f, 0.08f)
    EqPreset.MONITOR -> -2f * bump(t, 0.05f, 0.2f) + 3f * bump(t, 0.62f, 0.12f)
}

/**
 * The EQ preset as a curve. Switching presets springs the curve from one
 * shape into the next; the other presets stay behind as faint lines. Flat and
 * dashed when nothing is connected, gray and pulsing while connecting.
 */
@Composable
fun EqCurveHero(mode: HeroMode, preset: EqPreset?, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val colors = MaterialTheme.colorScheme
    val connected = mode == HeroMode.Connected

    // One weight per preset; the drawn curve is their weighted sum.
    val weights = EqPreset.entries.map { p ->
        animateFloatAsState(
            if (mode != HeroMode.Disconnected && p == preset) 1f else 0f,
            motion.spatialDefault(),
            label = "eqWeight",
        )
    }
    val ghostAlpha by animateFloatAsState(if (connected) 1f else 0f, motion.effectsDefault(), label = "ghost")
    val fillAlpha by animateFloatAsState(if (connected) 0.16f else 0f, motion.effectsDefault(), label = "fill")
    val lineColor by animateColorAsState(
        if (connected) colors.primary else colors.onSurfaceVariant,
        motion.effectsDefault(),
        label = "lineColor",
    )
    val pulse: State<Float> = if (mode == HeroMode.Connecting && !motion.reduced) {
        rememberInfiniteTransition(label = "connecting").animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val label = when {
        mode == HeroMode.Disconnected -> stringResource(R.string.hero_no_eq)
        preset == null -> stringResource(if (connected) R.string.hero_unknown_preset else R.string.eq)
        else -> preset.label
    }
    val curveDescription = stringResource(R.string.hero_curve_description, label)
    val grid = colors.outline
    val accent = colors.primary
    val dashed = mode == HeroMode.Disconnected

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Gutter)
            .clearAndSetSemantics { contentDescription = curveDescription },
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val mid = size.height * BASELINE
            val perDb = mid / RANGE_DB
            for (i in 1..3) {
                val x = size.width * i / 4
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx(), alpha = 0.5f)
            }
            drawLine(grid, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())

            if (ghostAlpha > 0f) {
                EqPreset.entries.filter { it != preset }.forEach { other ->
                    drawPath(
                        curvePath { t -> illustrativeCurve(other, t) * perDb },
                        grid,
                        alpha = ghostAlpha,
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }

            val current: (Float) -> Float = { t ->
                var level = 0f
                EqPreset.entries.forEachIndexed { i, p -> level += weights[i].value * illustrativeCurve(p, t) }
                level * perDb
            }
            val line = curvePath(current)
            if (fillAlpha > 0f) {
                val area = curvePath(current).apply {
                    lineTo(size.width, mid)
                    lineTo(0f, mid)
                    close()
                }
                drawPath(area, accent, alpha = fillAlpha)
            }
            drawPath(
                line,
                lineColor,
                alpha = pulse.value,
                style = Stroke(
                    3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())) else null,
                ),
            )
        }
    }
}

/** A path across the full width; [level] maps 0..1 to pixels above the baseline. */
private fun DrawScope.curvePath(level: (Float) -> Float): Path {
    val mid = size.height * BASELINE
    return Path().apply {
        for (i in 0..SAMPLES) {
            val t = i / SAMPLES.toFloat()
            val x = t * size.width
            val y = (mid - level(t)).coerceIn(0f, size.height)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
}

/**
 * Battery as ten pills, because the earbuds report it in 10 % steps. Pills
 * light up one after another when the level first shows; red at 20 % or less.
 */
@Composable
fun BatteryMeter(level: Int, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val colors = MaterialTheme.colorScheme
    val lit = ((level + 5) / 10).coerceIn(0, 10)
    val onColor by animateColorAsState(
        if (level <= LOW_BATTERY) colors.error else colors.primary,
        motion.effectsDefault(),
        label = "meterColor",
    )
    val offColor = colors.surfaceContainerHighest
    val segments = (0 until 10).map { i ->
        animateFloatAsState(
            if (i < lit) 1f else 0f,
            if (motion.reduced) tween(0) else tween(160, delayMillis = i * 35),
            label = "meterSegment",
        )
    }
    val description = stringResource(R.string.hero_battery_description, level)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 84.dp, height = 10.dp)) {
            drawSegments(segments, onColor, offColor)
        }
        Spacer(Modifier.width(8.dp))
        Text("$level%", style = MonoValue, color = colors.onSurfaceVariant)
    }
}

private fun DrawScope.drawSegments(segments: List<State<Float>>, on: Color, off: Color) {
    val gap = 2.dp.toPx()
    val width = (size.width - gap * (segments.size - 1)) / segments.size
    val radius = CornerRadius(minOf(width, size.height) / 2)
    segments.forEachIndexed { i, state ->
        val topLeft = Offset(i * (width + gap), 0f)
        drawRoundRect(off, topLeft, Size(width, size.height), radius)
        drawRoundRect(on, topLeft, Size(width, size.height), radius, alpha = state.value)
    }
}
