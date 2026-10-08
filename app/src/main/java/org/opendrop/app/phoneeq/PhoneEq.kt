package org.opendrop.app.phoneeq

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opendrop.dsp.AutoEq
import org.opendrop.dsp.EqCurve
import org.opendrop.dsp.EqPresetCurve
import org.opendrop.dsp.ParametricEq

/** Phone EQ settings: on/off and which curve. */
data class PhoneEqState(
    val enabled: Boolean = false,
    /** The chosen built-in preset, or null for [custom]. */
    val preset: EqPresetCurve? = EqPresetCurve.FLAT,
    /** The user's own curve: edited from a preset, or imported from AutoEQ. */
    val custom: EqCurve? = null,
) {
    val curve: EqCurve get() = preset?.eq ?: custom ?: EqPresetCurve.FLAT.eq

    /** The curve as an editable parametric EQ, if it is one. */
    val parametric: ParametricEq? get() = curve as? ParametricEq
}

/**
 * Phone-side EQ (roadmap M4): the state, kept in SharedPreferences. While
 * [PhoneEqState.enabled], [PhoneEqService] applies the curve to everything
 * the phone plays.
 */
class PhoneEq(private val context: Context) {
    private val prefs = context.getSharedPreferences("phone_eq", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<PhoneEqState> = _state.asStateFlow()

    /** True when the phone refused the effect (set by [PhoneEqService]). */
    val failed = MutableStateFlow(false)

    fun update(transform: (PhoneEqState) -> PhoneEqState) {
        val previous = _state.value
        val next = transform(previous)
        if (next == previous) return
        _state.value = next
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_PRESET, next.preset?.name ?: CUSTOM)
            .putString(KEY_CUSTOM, next.custom?.let(AutoEq::write))
            .apply()
        if (next.enabled && !previous.enabled) PhoneEqService.start(context)
    }

    /** Selects [curve] as the custom curve (an import, or an edit). */
    fun setCustom(curve: EqCurve) = update { it.copy(preset = null, custom = curve) }

    private fun load(): PhoneEqState {
        val presetName = prefs.getString(KEY_PRESET, EqPresetCurve.FLAT.name)
        val custom = prefs.getString(KEY_CUSTOM, null)?.let(AutoEq::parse)
        val preset = EqPresetCurve.entries.firstOrNull { it.name == presetName }
        return PhoneEqState(
            enabled = prefs.getBoolean(KEY_ENABLED, false) && supported,
            preset = if (preset == null && custom == null) EqPresetCurve.FLAT else preset,
            custom = custom,
        )
    }

    companion object {
        /** DynamicsProcessing arrived in Android 9. */
        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        private const val KEY_ENABLED = "enabled"
        private const val KEY_PRESET = "preset"
        private const val KEY_CUSTOM = "custom"
        private const val CUSTOM = "custom"
    }
}
