package org.opendrop.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.opendrop.app.MainActivity
import org.opendrop.app.OpenDropApplication
import org.opendrop.app.R
import org.opendrop.app.device.Connection
import org.opendrop.app.device.UiState
import org.opendrop.app.device.isActive

/**
 * Home-screen widget: earbuds name, battery and EQ preset (or connection
 * state). [OpenDropApplication] pushes every change; tapping opens the app.
 */
class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as OpenDropApplication
        manager.updateAppWidget(ids, views(context, WidgetContent.of(app.controller.state.value)))
    }

    /** What the widget shows, so updates only go out when it changes. */
    data class WidgetContent(val name: String, val battery: Int?, val status: String) {
        companion object {
            fun of(s: UiState): WidgetContent {
                val name = (s.selected ?: s.autoConnect)?.name ?: "OpenDrop"
                val status = when (s.connection) {
                    Connection.Connected ->
                        s.device.namedEqPreset?.let { "EQ ${it.label}" } ?: "Connected"
                    Connection.Connecting -> "Connecting…"
                    Connection.Reconnecting -> "Reconnecting…"
                    is Connection.Failed, Connection.Disconnected -> "Not connected"
                }
                // Android's level is stale once OpenDrop lets go of the earbuds.
                val battery = s.battery.takeIf { s.connection.isActive }
                return WidgetContent(name, battery, status)
            }
        }
    }

    companion object {
        fun update(context: Context, content: WidgetContent) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, views(context, content))
        }

        private fun views(context: Context, content: WidgetContent): RemoteViews {
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val battery = content.battery?.let { "$it %" } ?: "–"
            return RemoteViews(context.packageName, R.layout.widget_battery).apply {
                setTextViewText(R.id.widget_name, content.name)
                setTextViewText(R.id.widget_battery, battery)
                setTextViewText(R.id.widget_status, content.status)
                setContentDescription(
                    R.id.widget_root,
                    "${content.name}, battery ${content.battery?.let { "$it percent" } ?: "unknown"}, ${content.status}",
                )
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
