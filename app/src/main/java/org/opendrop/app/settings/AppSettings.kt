package org.opendrop.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings → Notifications and Automation. */
data class Behavior(
    /** Notify once when the earbuds' battery drops to [LOW_BATTERY_PERCENT]. */
    val lowBatteryAlert: Boolean = true,
    /** Accept commands from other apps (Tasker etc.) and broadcast state changes. */
    val automation: Boolean = false,
)

/**
 * Non-appearance settings. In SharedPreferences rather than DataStore because
 * broadcast receivers and the tile read them synchronously.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        Behavior(
            lowBatteryAlert = prefs.getBoolean(KEY_LOW_BATTERY, true),
            automation = prefs.getBoolean(KEY_AUTOMATION, false),
        ),
    )
    val state: StateFlow<Behavior> = _state.asStateFlow()

    fun update(transform: (Behavior) -> Behavior) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putBoolean(KEY_LOW_BATTERY, next.lowBatteryAlert)
            .putBoolean(KEY_AUTOMATION, next.automation)
            .apply()
    }

    companion object {
        const val LOW_BATTERY_PERCENT = 20
        private const val KEY_LOW_BATTERY = "low_battery_alert"
        private const val KEY_AUTOMATION = "automation"
    }
}
