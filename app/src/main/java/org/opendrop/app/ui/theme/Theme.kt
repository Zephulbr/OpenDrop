package org.opendrop.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import org.opendrop.app.settings.Appearance

/** Spacing from DESIGN.md (4 dp grid). */
object Dimens {
    val Gutter = 20.dp
    val SectionGap = 28.dp
    val RowMin = 56.dp
    val TopBar = 56.dp
}

val OpenDropShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Whether the app is currently drawn dark (system, or forced by the user). */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun OpenDropTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val neutrals = when {
        !dark -> LightNeutrals
        appearance.trueBlack -> BlackNeutrals
        else -> DarkNeutrals
    }
    val context = LocalContext.current
    val accent = remember(appearance.accent, neutrals, dark) {
        resolveAccent(appearance.accent, context, neutrals, dark)
    }
    val reduced = rememberReducedMotion()
    val motion = remember(reduced) { MotionTokens(reduced) }
    val scheme = neutralColorScheme(neutrals, accent, dark)

    // Animate the colors that change when the user switches theme or accent.
    val primary by animateColorAsState(scheme.primary, motion.effectsDefault(), label = "primary")
    val onPrimary by animateColorAsState(scheme.onPrimary, motion.effectsDefault(), label = "onPrimary")
    val container by animateColorAsState(scheme.primaryContainer, motion.effectsDefault(), label = "container")
    val background by animateColorAsState(scheme.background, motion.effectsDefault(), label = "background")
    val raised by animateColorAsState(scheme.surfaceContainerHigh, motion.effectsDefault(), label = "raised")
    val text by animateColorAsState(scheme.onSurface, motion.effectsDefault(), label = "text")
    val animated = scheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        background = background,
        surface = background,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        onBackground = text,
        onSurface = text,
    )

    val view = LocalView.current
    val haptics = remember(view, appearance.haptics) { Haptics(view, appearance.haptics) }

    CompositionLocalProvider(
        LocalMotion provides motion,
        LocalHaptics provides haptics,
        LocalDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = animated,
            typography = OpenDropTypography,
            shapes = OpenDropShapes,
            content = content,
        )
    }
}
