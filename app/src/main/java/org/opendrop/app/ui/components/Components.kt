package org.opendrop.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opendrop.app.R
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.app.ui.theme.LocalMotion
import org.opendrop.app.ui.theme.MonoValue

/** Buttons and rows shrink slightly while pressed. */
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val motion = LocalMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduced) pressedScale else 1f,
        animationSpec = motion.spatialFast(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = Dimens.Gutter).padding(bottom = 8.dp),
    )
}

/** Clickable row with an optional current value and a trailing chevron. */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.medium)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .heightIn(min = Dimens.RowMin)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        value?.let {
            Text(it, style = MonoValue, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Static label / value row. */
@Composable
fun InfoRow(title: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.RowMin)
            .padding(horizontal = Dimens.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MonoValue, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = Dimens.RowMin)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, interactionSource = interaction)
    }
}

/**
 * Pill-shaped segmented control. The accent indicator slides between segments;
 * its leading edge moves faster than its trailing edge, so it stretches as it
 * travels and settles back into a pill.
 */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val colors = MaterialTheme.colorScheme
    val index = options.indexOf(selected)
    var previous by remember { mutableIntStateOf(index) }
    val movingRight = index > previous
    SideEffect { previous = index }

    val target = index.coerceAtLeast(0).toFloat()
    val left by animateFloatAsState(
        target,
        if (movingRight) motion.spatialDefault() else motion.spatialFast(),
        label = "segmentLeft",
    )
    val right by animateFloatAsState(
        target + 1f,
        if (movingRight) motion.spatialFast() else motion.spatialDefault(),
        label = "segmentRight",
    )
    val indicatorAlpha by animateFloatAsState(if (index >= 0) 1f else 0f, motion.effectsFast(), label = "segmentAlpha")
    val indicatorColor = colors.primary

    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(4.dp)
            .drawBehind {
                val segment = size.width / options.size
                drawRoundRect(
                    color = indicatorColor,
                    alpha = indicatorAlpha,
                    topLeft = Offset(left * segment, 0f),
                    size = Size((right - left) * segment, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            },
    ) {
        options.forEachIndexed { i, option ->
            val isSelected = i == index
            val interaction = remember { MutableInteractionSource() }
            val textColor by animateColorAsState(
                if (isSelected) colors.onPrimary else colors.onSurface,
                motion.effectsFast(),
                label = "segmentText",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pressScale(interaction, pressedScale = 0.94f)
                    .clip(CircleShape)
                    .selectable(
                        selected = isSelected,
                        interactionSource = interaction,
                        indication = ripple(),
                        role = Role.RadioButton,
                        onClick = { if (!isSelected) onSelect(option) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** A number that rolls up or down when it changes. */
@Composable
fun RollingNumber(value: Int?, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val motion = LocalMotion.current
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            if (motion.reduced) {
                fadeIn(motion.effectsFast()) togetherWith fadeOut(motion.effectsFast())
            } else {
                val direction = if ((targetState ?: 0) >= (initialState ?: 0)) 1 else -1
                (slideInVertically(motion.spatialFast()) { h -> h * direction } + fadeIn(motion.effectsFast()))
                    .togetherWith(
                        slideOutVertically(motion.spatialFast()) { h -> -h * direction } + fadeOut(motion.effectsFast()),
                    )
                    .using(SizeTransform(clip = true))
            }
        },
        label = "rollingNumber",
    ) { number ->
        Text(number?.toString() ?: "–", style = style, color = color)
    }
}

/** Back arrow and title for sub-screens. */
@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(Dimens.TopBar)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * A slider drawn on a gradient (custom accent hue and saturation).
 * [value] is 0..1.
 */
@Composable
fun GradientSlider(
    value: Float,
    brush: Brush,
    onValueChange: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.White,
) {
    val update by rememberUpdatedState(onValueChange)
    val outline = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v ->
                    update(v.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> update((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    update((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .drawBehind {
                val trackHeight = 16.dp.toPx()
                val top = (size.height - trackHeight) / 2
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2),
                )
                val radius = size.height / 2 - 2.dp.toPx()
                val x = (value * size.width).coerceIn(radius, size.width - radius)
                drawCircle(thumbColor, radius = radius, center = Offset(x, size.height / 2))
                drawCircle(outline, radius = radius, center = Offset(x, size.height / 2), style = Stroke(2.dp.toPx()))
            },
    )
}

/** Small status dot used next to the connection label. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    val animated by animateColorAsState(color, LocalMotion.current.effectsDefault(), label = "statusDot")
    Box(modifier.size(8.dp).clip(CircleShape).background(animated))
}
