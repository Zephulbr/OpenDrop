package org.opendrop.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opendrop.app.ui.theme.LocalMotion

/** How the hero should look; see the state table in DESIGN.md. */
enum class HeroMode { Disconnected, Connecting, Connected }

private const val LOW_BATTERY = 20

/**
 * Two original, simple earbuds inside a battery ring. Reacts to connection
 * state, battery level and EQ changes. One accessibility node.
 */
@Composable
fun EarbudsHero(
    mode: HeroMode,
    battery: Int?,
    eqPresetId: Int?,
    description: String,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val colors = MaterialTheme.colorScheme
    val connected = mode == HeroMode.Connected

    val infinite = rememberInfiniteTransition(label = "hero")
    val pulse: State<Float> = if (mode == HeroMode.Connecting && !motion.reduced) {
        infinite.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse",
        )
    } else {
        remember { mutableFloatStateOf(0.6f) }
    }
    val float: State<Float> = if (!motion.reduced) {
        infinite.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "float",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val outlineAlpha by animateFloatAsState(
        if (mode == HeroMode.Disconnected) 0.4f else 1f,
        motion.effectsDefault(),
        label = "outlineAlpha",
    )
    val fill by animateFloatAsState(if (connected) 1f else 0f, motion.effectsDefault(), label = "fill")
    val spread by animateFloatAsState(if (connected) 0f else 1f, motion.spatialDefault(), label = "spread")
    val ring by animateFloatAsState(
        if (connected && battery != null) battery / 100f else 0f,
        motion.spatialSlow(),
        label = "ring",
    )
    val ringColor by animateColorAsState(
        if (battery != null && battery <= LOW_BATTERY) colors.error else colors.primary,
        motion.effectsDefault(),
        label = "ringColor",
    )

    // A short accent ripple when the EQ preset changes (not on first load).
    val ripple = remember { Animatable(1f) }
    var lastEq by remember { mutableStateOf(eqPresetId) }
    LaunchedEffect(eqPresetId) {
        val changed = eqPresetId != null && lastEq != null && eqPresetId != lastEq
        lastEq = eqPresetId
        if (changed && !motion.reduced) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
        }
    }

    val outline = colors.onSurface
    val track = colors.outline
    val budFill = colors.surfaceContainerHighest
    val background = colors.background
    val accent = colors.primary

    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description },
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val ringRadius = size.height * 0.42f
        val ringStroke = 4.dp.toPx()

        drawCircle(track, radius = ringRadius, center = center, style = Stroke(ringStroke), alpha = outlineAlpha)
        if (ring > 0f) {
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * ring,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(ringStroke, cap = StrokeCap.Round),
            )
        }
        val r = ripple.value
        if (r < 1f) {
            drawCircle(
                accent,
                radius = ringRadius * (0.55f + 0.6f * r),
                center = center,
                style = Stroke(2.dp.toPx()),
                alpha = (1f - r) * 0.6f,
            )
        }

        val budRadius = ringRadius * 0.34f
        val bodyCenterY = center.y + float.value * 2.5.dp.toPx()
        val offsetX = budRadius * 1.1f + spread * 6.dp.toPx()
        val lineAlpha = if (mode == HeroMode.Connecting) pulse.value else outlineAlpha
        val style = BudStyle(outline, budFill, background, lineAlpha, fill)
        drawBud(Offset(center.x - offsetX, bodyCenterY), budRadius, facing = 1f, style)
        drawBud(Offset(center.x + offsetX, bodyCenterY), budRadius, facing = -1f, style)
    }
}

private class BudStyle(
    val line: Color,
    val fill: Color,
    val background: Color,
    val lineAlpha: Float,
    val fillAlpha: Float,
)

/** One bud: round body, a nozzle pointing toward the other bud, and a touch plate. */
private fun DrawScope.drawBud(body: Offset, radius: Float, facing: Float, style: BudStyle) {
    val stroke = Stroke(2.dp.toPx())
    val nozzle = Offset(body.x + facing * radius * 0.78f, body.y - radius * 0.55f)
    val nozzleRadius = radius * 0.42f

    // Nozzle sits behind the body; the body's background fill hides its overlap.
    drawCircle(style.background, nozzleRadius, nozzle)
    drawCircle(style.fill, nozzleRadius, nozzle, alpha = style.fillAlpha)
    drawCircle(style.line, nozzleRadius, nozzle, alpha = style.lineAlpha, style = stroke)

    drawCircle(style.background, radius, body)
    drawCircle(style.fill, radius, body, alpha = style.fillAlpha)
    drawCircle(style.line, radius, body, alpha = style.lineAlpha, style = stroke)

    drawCircle(style.line, radius * 0.48f, body, alpha = style.lineAlpha * 0.5f, style = stroke)
}
