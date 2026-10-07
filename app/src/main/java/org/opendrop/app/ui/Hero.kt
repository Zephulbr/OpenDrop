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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
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
    val tipFill = colors.outline
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

        val budRadius = ringRadius * 0.31f
        val bodyCenterY = center.y + float.value * 2.5.dp.toPx()
        val offsetX = budRadius * 1.7f + spread * 6.dp.toPx()
        val lineAlpha = if (mode == HeroMode.Connecting) pulse.value else outlineAlpha
        val style = BudStyle(outline, budFill, tipFill, background, lineAlpha, fill)
        drawBud(Offset(center.x - offsetX, bodyCenterY), budRadius, facing = 1f, style)
        drawBud(Offset(center.x + offsetX, bodyCenterY), budRadius, facing = -1f, style)
    }
}

private class BudStyle(
    val line: Color,
    val fill: Color,
    val tipFill: Color,
    val background: Color,
    val lineAlpha: Float,
    val fillAlpha: Float,
)

/** Outward tilt of each bud, degrees. */
private const val BUD_TILT = -18f

/** Direction of the nozzle and ear tip: inward and down, degrees below horizontal. */
private const val TIP_ANGLE = 55f

/**
 * One bud, drawn in units of [radius] with +x pointing toward the other bud:
 * a rounded shell, a short nozzle and an ear tip angled inward and down, a
 * highlight on the shell and a mic dot. [facing] mirrors it for the right bud.
 */
private fun DrawScope.drawBud(position: Offset, radius: Float, facing: Float, style: BudStyle) {
    val stroke = Stroke(2.dp.toPx())
    val detail = lerp(style.line, style.fill, 0.4f)
    withTransform({
        translate(position.x, position.y)
        rotate(BUD_TILT * facing, pivot = Offset.Zero)
        scale(facing, 1f, pivot = Offset.Zero)
    }) {
        val angle = Math.toRadians(TIP_ANGLE.toDouble())
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        // Back to front; each shape's background fill hides what's behind it.
        shape(Offset(0.78f * dx, 0.78f * dy) * radius, 0.42f * radius, 0.30f * radius, TIP_ANGLE, style.fill, style, stroke)
        shape(Offset(1.16f * dx, 1.16f * dy) * radius, 0.34f * radius, 0.52f * radius, TIP_ANGLE, style.tipFill, style, stroke)
        shape(Offset.Zero, 0.95f * radius, 1.12f * radius, 0f, style.fill, style, stroke)

        val hx = 0.68f * 0.95f * radius
        val hy = 0.68f * 1.12f * radius
        drawArc(
            detail,
            startAngle = 200f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(-hx, -hy),
            size = Size(hx * 2, hy * 2),
            alpha = style.lineAlpha,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(detail, 0.07f * radius, Offset(-0.30f * radius, 0.62f * radius), alpha = style.lineAlpha)
    }
}

/** An oval with semi-axes [a] x [b] at [center], rotated by [degrees]: background, fill, outline. */
private fun DrawScope.shape(
    center: Offset,
    a: Float,
    b: Float,
    degrees: Float,
    fill: Color,
    style: BudStyle,
    stroke: Stroke,
) {
    rotate(degrees, pivot = center) {
        val topLeft = Offset(center.x - a, center.y - b)
        val size = Size(a * 2, b * 2)
        drawOval(style.background, topLeft, size)
        drawOval(fill, topLeft, size, alpha = style.fillAlpha)
        drawOval(style.line, topLeft, size, alpha = style.lineAlpha, style = stroke)
    }
}
