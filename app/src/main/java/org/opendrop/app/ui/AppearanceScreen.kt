package org.opendrop.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opendrop.app.settings.Appearance
import org.opendrop.app.ui.components.GradientSlider
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.components.SegmentedSelector
import org.opendrop.app.ui.components.SwitchRow
import org.opendrop.app.ui.components.pressScale
import org.opendrop.app.ui.theme.AccentChoice
import org.opendrop.app.ui.theme.AccentPreset
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.app.ui.theme.LocalDarkTheme
import org.opendrop.app.ui.theme.LocalHaptics
import org.opendrop.app.ui.theme.LocalMotion
import org.opendrop.app.ui.theme.ThemeMode
import org.opendrop.app.ui.theme.customSeed
import org.opendrop.app.ui.theme.dynamicAccentAvailable
import org.opendrop.app.ui.theme.hueSaturationOf
import org.opendrop.app.ui.theme.presetColor
import org.opendrop.app.ui.theme.systemAccent

/** Starting point for Custom when the user hasn't picked one yet. */
private val DEFAULT_CUSTOM = AccentChoice.Custom(hue = 200f, saturation = 0.8f)

@Composable
fun AppearanceScreen(
    appearance: Appearance,
    onChange: (debounce: Boolean, transform: (Appearance) -> Appearance) -> Unit,
    onBack: () -> Unit,
) {
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Appearance", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionTitle("Theme")
            SegmentedSelector(
                options = ThemeMode.entries,
                selected = appearance.theme,
                label = { it.label },
                onSelect = { mode ->
                    haptics.segment()
                    onChange(false) { it.copy(theme = mode) }
                },
                modifier = Modifier.padding(horizontal = Dimens.Gutter),
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "True black",
                subtitle = "Pure black background in dark mode",
                checked = appearance.trueBlack,
                onCheckedChange = { on -> onChange(false) { it.copy(trueBlack = on) } },
            )

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("Accent")
            AccentPicker(appearance.accent) { choice, debounce ->
                onChange(debounce) { it.copy(accent = choice) }
            }

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("Feedback")
            SwitchRow(
                title = "Haptics",
                subtitle = "Light ticks on presets, slider steps and connect",
                checked = appearance.haptics,
                onCheckedChange = { on -> onChange(false) { it.copy(haptics = on) } },
            )

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("About")
            Text(
                "OpenDrop is free software under the GPL-3.0. Not affiliated with Moondrop. " +
                    "Fonts: Inter and JetBrains Mono, SIL Open Font License 1.1.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.Gutter),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(current: AccentChoice, onPick: (AccentChoice, Boolean) -> Unit) {
    val dark = LocalDarkTheme.current
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val custom = current as? AccentChoice.Custom

    val selectedLabel = when (current) {
        AccentChoice.System -> "System"
        is AccentChoice.Preset -> current.preset.label
        is AccentChoice.Custom -> "Custom"
    }

    Column(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (dynamicAccentAvailable) {
                val system = remember(dark) { systemAccent(context, dark) } ?: Color.Gray
                Swatch(
                    label = "System",
                    fill = SolidColor(system),
                    selected = current == AccentChoice.System,
                    marker = true,
                ) {
                    haptics.segment()
                    onPick(AccentChoice.System, false)
                }
            }
            AccentPreset.entries.forEach { preset ->
                val color = presetColor(preset, dark)
                Swatch(
                    label = preset.label,
                    fill = SolidColor(color),
                    selected = current == AccentChoice.Preset(preset),
                ) {
                    haptics.segment()
                    onPick(AccentChoice.Preset(preset), false)
                }
            }
            Swatch(
                label = "Custom",
                fill = Brush.sweepGradient(hueStops()),
                selected = custom != null,
            ) {
                haptics.segment()
                if (custom == null) {
                    // Start from the color in use so switching to Custom doesn't jump.
                    val seed = (current as? AccentChoice.Preset)?.let { presetColor(it.preset, dark) }
                    val start = seed?.let(::hueSaturationOf)
                        ?.let { (h, s) -> AccentChoice.Custom(h, s) } ?: DEFAULT_CUSTOM
                    onPick(start, false)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            selectedLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
        )

        AnimatedVisibility(
            visible = custom != null,
            enter = expandVertically(motion.spatialDefault()) + fadeIn(motion.effectsDefault()),
            exit = shrinkVertically(motion.spatialDefault()) + fadeOut(motion.effectsFast()),
        ) {
            val hue = custom?.hue ?: DEFAULT_CUSTOM.hue
            val saturation = custom?.saturation ?: DEFAULT_CUSTOM.saturation
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Gutter)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GradientSlider(
                    value = hue / 360f,
                    brush = Brush.horizontalGradient(hueStops()),
                    onValueChange = { v -> onPick(AccentChoice.Custom(v * 360f, saturation), true) },
                    contentDescription = "Hue",
                    thumbColor = customSeed(hue, saturation),
                )
                GradientSlider(
                    value = saturation,
                    brush = Brush.horizontalGradient(listOf(customSeed(hue, 0f), customSeed(hue, 1f))),
                    onValueChange = { v -> onPick(AccentChoice.Custom(hue, v), true) },
                    contentDescription = "Vibrance",
                    thumbColor = customSeed(hue, saturation),
                )
                Text(
                    "Brightness is tuned per theme so text stays readable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun hueStops(): List<Color> = (0..6).map { customSeed(it * 60f % 360f, 1f) }

/** A round accent swatch. The selected one gets a ring that grows in. */
@Composable
private fun Swatch(
    label: String,
    fill: Brush,
    selected: Boolean,
    marker: Boolean = false,
    onClick: () -> Unit,
) {
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, motion.spatialFast(), label = "swatchRing")
    val inset by animateDpAsState(if (selected) 6.dp else 0.dp, motion.spatialFast(), label = "swatchInset")
    val ringColor = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(48.dp)
            .pressScale(interaction, pressedScale = 0.9f)
            .clip(CircleShape)
            .drawBehind {
                val width = ring.toPx()
                if (width > 0f) {
                    drawCircle(ringColor, radius = size.minDimension / 2 - width / 2, style = Stroke(width))
                }
            }
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(inset.coerceAtLeast(0.dp))
                .fillMaxSize()
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            if (marker) {
                // Marks "System" apart from a preset of the same color.
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}
