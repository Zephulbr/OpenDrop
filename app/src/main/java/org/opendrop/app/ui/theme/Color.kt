package org.opendrop.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

/** Accent presets from DESIGN.md. Each has its own tone per theme so it stays readable. */
enum class AccentPreset(val label: String, val dark: Color, val light: Color) {
    HotPink("Hot pink", Color(0xFFFF2E88), Color(0xFFD6006A)),
    MoonViolet("Moon violet", Color(0xFFB69CFF), Color(0xFF6B4FD8)),
    SignalOrange("Signal orange", Color(0xFFFF8A3D), Color(0xFFC24E00)),
    CoolCyan("Cool cyan", Color(0xFF4DD8F0), Color(0xFF007A8F)),
    Lime("Lime", Color(0xFFC6F24E), Color(0xFF4F7A00)),
}

sealed interface AccentChoice {
    /** The system accent (Android 12+). */
    data object System : AccentChoice
    data class Preset(val preset: AccentPreset) : AccentChoice
    /** A user-picked color; the tone is solved per theme for contrast. */
    data class Custom(val hue: Float, val saturation: Float) : AccentChoice
}

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
val dynamicAccentAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Neutral palette for one theme. Never tinted by the accent. */
data class Neutrals(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val outline: Color,
    val text: Color,
    val textSecondary: Color,
    val error: Color,
)

val LightNeutrals = Neutrals(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFEFEFEF),
    outline = Color(0xFFDADADA),
    text = Color(0xFF111111),
    textSecondary = Color(0xFF5C5C5C),
    error = Color(0xFFC62828),
)

val DarkNeutrals = Neutrals(
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1C),
    raised = Color(0xFF262626),
    outline = Color(0xFF333333),
    text = Color(0xFFF2F2F2),
    textSecondary = Color(0xFFA3A3A3),
    error = Color(0xFFFF6B6B),
)

val BlackNeutrals = Neutrals(
    background = Color(0xFF000000),
    surface = Color(0xFF0E0E0E),
    raised = Color(0xFF1A1A1A),
    outline = Color(0xFF262626),
    text = Color(0xFFF2F2F2),
    textSecondary = Color(0xFFA3A3A3),
    error = Color(0xFFFF6B6B),
)

private const val MIN_CONTRAST = 4.5f

fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Black or white, whichever reads better on [background]. */
fun readableOn(background: Color): Color =
    if (contrast(Color.Black, background) >= contrast(Color.White, background)) Color.Black else Color.White

/** The color shown for a custom accent before the tone is solved, e.g. on the picker swatch. */
fun customSeed(hue: Float, saturation: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, 0.5f)))

/** Moves the lightness of a hue/saturation pair until it reaches 4.5:1 against [background]. */
fun solveAccent(hue: Float, saturation: Float, background: Color, dark: Boolean): Color {
    var lightness = 0.5f
    var color = customSeed(hue, saturation)
    while (contrast(color, background) < MIN_CONTRAST && lightness in 0f..1f) {
        lightness += if (dark) 0.02f else -0.02f
        color = Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness.coerceIn(0f, 1f))))
    }
    return color
}

fun hueSaturationOf(color: Color): Pair<Float, Float> {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    return hsl[0] to hsl[1]
}

fun presetColor(preset: AccentPreset, dark: Boolean): Color = if (dark) preset.dark else preset.light

/** The system accent for this theme, or null below Android 12. */
fun systemAccent(context: Context, dark: Boolean): Color? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        null
    }

fun resolveAccent(choice: AccentChoice, context: Context, neutrals: Neutrals, dark: Boolean): Color =
    when (choice) {
        AccentChoice.System -> systemAccent(context, dark) ?: presetColor(AccentPreset.HotPink, dark)
        is AccentChoice.Preset -> presetColor(choice.preset, dark)
        is AccentChoice.Custom -> solveAccent(choice.hue, choice.saturation, neutrals.background, dark)
    }

/** A Material color scheme with neutral surfaces and [accent] as the only color. */
fun neutralColorScheme(n: Neutrals, accent: Color, dark: Boolean): ColorScheme {
    val onAccent = readableOn(accent)
    val container = lerp(n.surface, accent, 0.18f)
    val errorContainer = lerp(n.surface, n.error, 0.14f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = n.text,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = n.text,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = container,
        onTertiaryContainer = n.text,
        background = n.background,
        onBackground = n.text,
        surface = n.background,
        onSurface = n.text,
        surfaceVariant = n.raised,
        onSurfaceVariant = n.textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = n.text,
        inverseOnSurface = n.background,
        error = n.error,
        onError = readableOn(n.error),
        errorContainer = errorContainer,
        onErrorContainer = n.text,
        outline = n.outline,
        outlineVariant = n.outline,
        scrim = Color.Black,
        surfaceBright = n.surface,
        surfaceDim = n.background,
        surfaceContainerLowest = n.background,
        surfaceContainerLow = n.surface,
        surfaceContainer = n.surface,
        surfaceContainerHigh = n.raised,
        surfaceContainerHighest = n.raised,
    )
}
