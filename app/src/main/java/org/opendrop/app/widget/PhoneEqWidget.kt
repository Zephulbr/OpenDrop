package org.opendrop.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import org.opendrop.app.OpenDropApplication
import org.opendrop.app.R
import org.opendrop.app.phoneeq.PhoneEq
import org.opendrop.app.phoneeq.PhoneEqState

/** Phone EQ widget: on/off and the current curve; the button toggles it. */
class PhoneEqWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as OpenDropApplication
        manager.updateAppWidget(ids, views(context, Content.of(app.phoneEq.state.value)))
    }

    data class Content(val enabled: Boolean, val curve: String) {
        companion object {
            fun of(s: PhoneEqState) = Content(s.enabled, s.preset?.label ?: "Custom")
        }
    }

    companion object {
        fun update(context: Context, content: Content) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, PhoneEqWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, views(context, content))
        }

        private fun views(context: Context, content: Content): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_phone_eq).apply {
                val state = if (content.enabled) "On · ${content.curve}" else context.getString(R.string.widget_off)
                setTextViewText(R.id.phone_eq_state, state)
                setTextViewText(
                    R.id.phone_eq_toggle,
                    context.getString(if (content.enabled) R.string.widget_turn_off else R.string.widget_turn_on),
                )
                setInt(
                    R.id.phone_eq_toggle,
                    "setBackgroundResource",
                    if (content.enabled) R.drawable.widget_chip_on else R.drawable.widget_chip_off,
                )
                setTextColor(
                    R.id.phone_eq_toggle,
                    ContextCompat.getColor(context, if (content.enabled) R.color.widget_on_accent else R.color.widget_primary),
                )
                val toggle = if (PhoneEq.supported) {
                    WidgetActionReceiver.togglePhoneEq(context)
                } else {
                    WidgetActionReceiver.openApp(context)
                }
                setOnClickPendingIntent(R.id.phone_eq_toggle, toggle)
                setOnClickPendingIntent(R.id.phone_eq_root, WidgetActionReceiver.openApp(context))
            }
    }
}
