package org.opendrop.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import org.opendrop.app.OpenDropApplication
import org.opendrop.app.R
import org.opendrop.app.device.Connection
import org.opendrop.app.device.UiState
import org.opendrop.protocol.EqPreset

/**
 * Earbuds EQ widget: one button per preset the earbuds offer, the current one
 * highlighted. While disconnected it shows a status line that connects.
 */
class EqWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as OpenDropApplication
        manager.updateAppWidget(ids, views(context, Content.of(context.resources, app.controller.state.value)))
    }

    data class Content(
        val name: String,
        /** Presets with a button; empty shows [status] instead. */
        val presets: List<EqPreset>,
        val current: EqPreset?,
        val status: String,
        /** A tap on the widget connects (disconnected, with a remembered device); otherwise it opens the app. */
        val tapConnects: Boolean,
    ) {
        companion object {
            fun of(res: Resources, s: UiState): Content {
                val name = (s.selected ?: s.autoConnect)?.name ?: "OpenDrop"
                val connected = s.connection == Connection.Connected
                val presets = if (connected) s.device.switchableEqPresets else emptyList()
                val disconnected = s.connection == Connection.Disconnected || s.connection is Connection.Failed
                val tapConnects = disconnected && s.autoConnect != null
                val status = when {
                    connected -> if (presets.isEmpty()) res.getString(R.string.widget_eq_unsupported) else ""
                    !disconnected -> res.getString(R.string.status_connecting)
                    tapConnects -> res.getString(R.string.widget_tap_connect)
                    else -> res.getString(R.string.widget_tap_open)
                }
                return Content(name, presets, s.device.namedEqPreset, status, tapConnects)
            }
        }
    }

    companion object {
        private val buttons = mapOf(
            EqPreset.REFERENCE to R.id.eq_reference,
            EqPreset.BASSHEAD to R.id.eq_basshead,
            EqPreset.MONITOR to R.id.eq_monitor,
        )

        fun update(context: Context, content: Content) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, EqWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, views(context, content))
        }

        private fun views(context: Context, content: Content): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_eq).apply {
                setTextViewText(R.id.eq_name, "${content.name} · ${context.getString(R.string.eq)}")
                val showButtons = content.presets.isNotEmpty()
                setViewVisibility(R.id.eq_buttons, if (showButtons) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.eq_status, if (showButtons) View.GONE else View.VISIBLE)
                setTextViewText(R.id.eq_status, content.status)
                buttons.forEach { (preset, id) ->
                    val offered = preset in content.presets
                    setViewVisibility(id, if (offered) View.VISIBLE else View.GONE)
                    val on = preset == content.current
                    setInt(id, "setBackgroundResource", if (on) R.drawable.widget_chip_on else R.drawable.widget_chip_off)
                    setTextColor(
                        id,
                        ContextCompat.getColor(context, if (on) R.color.widget_on_accent else R.color.widget_primary),
                    )
                    setContentDescription(id, if (on) context.getString(R.string.widget_selected, preset.label) else preset.label)
                    setOnClickPendingIntent(id, WidgetActionReceiver.setEq(context, preset))
                }
                val tap = if (content.tapConnects) WidgetActionReceiver.connect(context) else WidgetActionReceiver.openApp(context)
                setOnClickPendingIntent(R.id.eq_root, tap)
            }
    }
}
