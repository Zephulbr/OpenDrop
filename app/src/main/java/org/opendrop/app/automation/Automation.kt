package org.opendrop.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Locale
import org.opendrop.app.OpenDropApplication
import org.opendrop.app.device.Connection
import org.opendrop.app.device.UiState
import org.opendrop.protocol.EqPreset

/**
 * Broadcast intents for Tasker, MacroDroid and similar apps. Off until the
 * user turns on Settings → Automation → Allow other apps; while off, commands
 * are ignored and no state is broadcast.
 */
object Automation {
    const val PACKAGE = "org.opendrop.app"

    const val ACTION_SET_EQ = "org.opendrop.app.action.SET_EQ"
    const val ACTION_NEXT_EQ = "org.opendrop.app.action.NEXT_EQ"
    const val ACTION_CONNECT = "org.opendrop.app.action.CONNECT"
    const val ACTION_DISCONNECT = "org.opendrop.app.action.DISCONNECT"

    /**
     * [ACTION_SET_EQ]: preset name (reference, basshead, monitor) or id.
     * [EVENT_STATE]: preset name in lower case, or empty if unknown.
     */
    const val EXTRA_PRESET = "preset"

    /** Sent when the connection, battery or EQ preset changes. */
    const val EVENT_STATE = "org.opendrop.app.event.STATE"

    /** connected, connecting, reconnecting, disconnected or failed. */
    const val EXTRA_CONNECTION = "connection"
    const val EXTRA_DEVICE = "device"

    /** Percent, or -1 if unknown. */
    const val EXTRA_BATTERY = "battery"

    /** What [EVENT_STATE] carries, so it's only sent when this changes. */
    data class State(val connection: String, val device: String, val battery: Int, val preset: String) {
        fun toIntent(): Intent = Intent(EVENT_STATE)
            .putExtra(EXTRA_CONNECTION, connection)
            .putExtra(EXTRA_DEVICE, device)
            .putExtra(EXTRA_BATTERY, battery)
            .putExtra(EXTRA_PRESET, preset)

        companion object {
            fun of(s: UiState) = State(
                connection = when (s.connection) {
                    Connection.Connected -> "connected"
                    Connection.Connecting -> "connecting"
                    Connection.Reconnecting -> "reconnecting"
                    Connection.Disconnected -> "disconnected"
                    is Connection.Failed -> "failed"
                },
                device = (s.selected ?: s.autoConnect)?.name.orEmpty(),
                battery = s.battery.takeIf { s.connection == Connection.Connected } ?: -1,
                preset = if (s.connection == Connection.Connected) {
                    s.device.namedEqPreset?.name?.lowercase(Locale.ROOT).orEmpty()
                } else {
                    ""
                },
            )
        }
    }
}

/** Receives the [Automation] commands. Exported; does nothing unless the user allowed it. */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as OpenDropApplication
        if (!app.settings.state.value.automation) {
            Log.i(TAG, "Ignored ${intent.action}: automation is off in Settings")
            return
        }
        val controller = app.controller
        val done = when (intent.action) {
            Automation.ACTION_SET_EQ -> {
                @Suppress("DEPRECATION") // the value may be a string or a number
                val preset = EqPreset.parse(intent.extras?.get(Automation.EXTRA_PRESET)?.toString())
                if (preset != null && preset in controller.state.value.device.switchableEqPresets) {
                    controller.setEq(preset)
                    true
                } else {
                    false
                }
            }
            Automation.ACTION_NEXT_EQ -> controller.nextEq()
            Automation.ACTION_CONNECT -> controller.connectRemembered()
            Automation.ACTION_DISCONNECT -> {
                controller.disconnect()
                true
            }
            else -> false
        }
        if (!done) Log.i(TAG, "${intent.action} had no effect in the current state")
    }

    private companion object {
        const val TAG = "Automation"
    }
}
