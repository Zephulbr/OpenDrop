package org.opendrop.app.settings

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.opendrop.app.ui.theme.AccentChoice
import org.opendrop.app.ui.theme.AccentPreset
import org.opendrop.app.ui.theme.ThemeMode
import org.opendrop.app.ui.theme.dynamicAccentAvailable

/** Settings → Appearance (DESIGN.md). */
data class Appearance(
    val theme: ThemeMode = ThemeMode.System,
    val trueBlack: Boolean = false,
    val accent: AccentChoice = defaultAccent(),
    val haptics: Boolean = true,
)

/** System accent where Android has one (12+), hot pink otherwise. */
fun defaultAccent(): AccentChoice =
    if (dynamicAccentAvailable) AccentChoice.System else AccentChoice.Preset(AccentPreset.HotPink)

private val Context.appearanceStore by preferencesDataStore(name = "appearance")

private val THEME = stringPreferencesKey("theme")
private val TRUE_BLACK = booleanPreferencesKey("true_black")
private val ACCENT = stringPreferencesKey("accent")
private val HAPTICS = booleanPreferencesKey("haptics")

private fun AccentChoice.encode(): String = when (this) {
    AccentChoice.System -> "system"
    is AccentChoice.Preset -> "preset:${preset.name}"
    is AccentChoice.Custom -> "custom:$hue,$saturation"
}

private fun decodeAccent(value: String?): AccentChoice {
    if (value == null) return defaultAccent()
    val parsed: AccentChoice? = when {
        value == "system" -> AccentChoice.System
        value.startsWith("preset:") -> {
            val name = value.removePrefix("preset:")
            AccentPreset.entries.firstOrNull { it.name == name }?.let { AccentChoice.Preset(it) }
        }
        value.startsWith("custom:") -> {
            val parts = value.removePrefix("custom:").split(',')
            val hue = parts.getOrNull(0)?.toFloatOrNull()
            val saturation = parts.getOrNull(1)?.toFloatOrNull()
            if (hue != null && saturation != null) AccentChoice.Custom(hue, saturation) else null
        }
        else -> null
    }
    // A backup restored on an older phone may say "system" where there is none.
    return if (parsed == null || (parsed == AccentChoice.System && !dynamicAccentAvailable)) defaultAccent() else parsed
}

private fun Preferences.toAppearance() = Appearance(
    theme = ThemeMode.entries.firstOrNull { it.name == this[THEME] } ?: ThemeMode.System,
    trueBlack = this[TRUE_BLACK] ?: false,
    accent = decodeAccent(this[ACCENT]),
    haptics = this[HAPTICS] ?: true,
)

class AppearanceViewModel(app: Application) : AndroidViewModel(app) {
    private val store = app.appearanceStore

    /** Null until the stored settings are read (a few ms at startup). */
    private val _state = MutableStateFlow<Appearance?>(null)
    val state: StateFlow<Appearance?> = _state.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            _state.value = store.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .first()
                .toAppearance()
        }
    }

    /**
     * Applies a change immediately. With [debounce], the write waits for the
     * value to settle (used while dragging the custom color sliders).
     */
    fun update(debounce: Boolean = false, transform: (Appearance) -> Appearance) {
        val next = transform(_state.value ?: return)
        _state.value = next
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (debounce) delay(SAVE_DEBOUNCE_MS)
            store.edit { p ->
                p[THEME] = next.theme.name
                p[TRUE_BLACK] = next.trueBlack
                p[ACCENT] = next.accent.encode()
                p[HAPTICS] = next.haptics
            }
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 200L
    }
}
