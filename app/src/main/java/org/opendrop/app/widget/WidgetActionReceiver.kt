package org.opendrop.app.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.opendrop.app.MainActivity
import org.opendrop.app.OpenDropApplication
import org.opendrop.protocol.EqPreset

/**
 * Taps on the widgets' buttons. Not exported: only our own widget
 * PendingIntents reach it, so unlike automation it needs no opt-in.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as OpenDropApplication
        when (intent.action) {
            ACTION_SET_EQ -> {
                val preset = EqPreset.parse(intent.getStringExtra(EXTRA_PRESET)) ?: return
                if (preset in app.controller.state.value.device.switchableEqPresets) app.controller.setEq(preset)
            }
            ACTION_CONNECT -> app.controller.connectRemembered()
            ACTION_TOGGLE_PHONE_EQ -> app.phoneEq.update { it.copy(enabled = !it.enabled) }
        }
    }

    companion object {
        private const val ACTION_SET_EQ = "org.opendrop.app.widget.SET_EQ"
        private const val ACTION_CONNECT = "org.opendrop.app.widget.CONNECT"
        private const val ACTION_TOGGLE_PHONE_EQ = "org.opendrop.app.widget.TOGGLE_PHONE_EQ"
        private const val EXTRA_PRESET = "preset"

        fun setEq(context: Context, preset: EqPreset): PendingIntent =
            broadcast(context, ACTION_SET_EQ, REQUEST_EQ + preset.id) { putExtra(EXTRA_PRESET, preset.name) }

        fun connect(context: Context): PendingIntent = broadcast(context, ACTION_CONNECT, REQUEST_CONNECT)

        fun togglePhoneEq(context: Context): PendingIntent = broadcast(context, ACTION_TOGGLE_PHONE_EQ, REQUEST_PHONE_EQ)

        fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun broadcast(context: Context, action: String, request: Int, extras: Intent.() -> Unit = {}) =
            PendingIntent.getBroadcast(
                context,
                request,
                Intent(context, WidgetActionReceiver::class.java).setAction(action).apply(extras),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private const val REQUEST_OPEN = 100
        private const val REQUEST_CONNECT = 101
        private const val REQUEST_PHONE_EQ = 102
        private const val REQUEST_EQ = 110
    }
}
