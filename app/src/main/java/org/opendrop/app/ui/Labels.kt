package org.opendrop.app.ui

import androidx.annotation.StringRes
import org.opendrop.app.R
import org.opendrop.app.ui.theme.AccentPreset
import org.opendrop.app.ui.theme.ThemeMode
import org.opendrop.dsp.EqPresetCurve
import org.opendrop.dsp.FilterType
import org.opendrop.protocol.Capability
import org.opendrop.protocol.LinkError

// Translated names for the core modules' enums. Core is plain Kotlin and
// keeps English labels for reports; the app shows these.

@StringRes
fun LinkError.adviceRes(): Int = when (this) {
    LinkError.NO_ANSWER -> R.string.link_error_no_answer
    LinkError.REFUSED -> R.string.link_error_refused
    LinkError.PERMISSION -> R.string.link_error_permission
    LinkError.BLUETOOTH_OFF -> R.string.link_error_bluetooth_off
    LinkError.OTHER -> R.string.link_error_other
}

@StringRes
fun Capability.labelRes(): Int = when (this) {
    Capability.EQ_PRESETS -> R.string.capability_eq_presets
    Capability.BATTERY -> R.string.capability_battery
    Capability.NOISE_CONTROL -> R.string.capability_noise_control
    Capability.TOUCH_CONTROLS -> R.string.capability_touch_controls
    Capability.FIND_EARBUDS -> R.string.capability_find_earbuds
    Capability.CODEC -> R.string.capability_codec
    Capability.AUTO_POWER_OFF -> R.string.capability_auto_power_off
    Capability.LED -> R.string.capability_led
    Capability.WEAR_SENSOR -> R.string.capability_wear_sensor
    Capability.MULTIPOINT -> R.string.capability_multipoint
    Capability.SPATIAL_AUDIO -> R.string.capability_spatial_audio
    Capability.DYNAMIC_BASS -> R.string.capability_dynamic_bass
    Capability.CHANNEL_SWAP -> R.string.capability_channel_swap
    Capability.DUAL_MIC_NOISE_REDUCTION -> R.string.capability_dual_mic
    Capability.VOICE_PROMPTS -> R.string.capability_voice_prompts
}

@StringRes
fun EqPresetCurve.labelRes(): Int = when (this) {
    EqPresetCurve.FLAT -> R.string.curve_flat
    EqPresetCurve.BASS -> R.string.curve_bass
    EqPresetCurve.WARM -> R.string.curve_warm
    EqPresetCurve.V_SHAPE -> R.string.curve_v_shape
    EqPresetCurve.VOCAL -> R.string.curve_vocal
    EqPresetCurve.TREBLE -> R.string.curve_treble
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

@StringRes
fun AccentPreset.labelRes(): Int = when (this) {
    AccentPreset.HotPink -> R.string.accent_hot_pink
    AccentPreset.MoonViolet -> R.string.accent_moon_violet
    AccentPreset.SignalOrange -> R.string.accent_signal_orange
    AccentPreset.CoolCyan -> R.string.accent_cool_cyan
    AccentPreset.Lime -> R.string.accent_lime
}

@StringRes
fun FilterType.labelRes(): Int = when (this) {
    FilterType.PEAK -> R.string.filter_peak
    FilterType.LOW_SHELF -> R.string.filter_low_shelf
    FilterType.HIGH_SHELF -> R.string.filter_high_shelf
    FilterType.LOW_PASS -> R.string.filter_low_pass
    FilterType.HIGH_PASS -> R.string.filter_high_pass
}
