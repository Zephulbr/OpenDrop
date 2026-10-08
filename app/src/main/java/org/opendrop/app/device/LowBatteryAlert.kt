package org.opendrop.app.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.opendrop.app.MainActivity
import org.opendrop.app.R
import org.opendrop.app.settings.AppSettings

/**
 * Posts one notification when the earbuds' battery drops to
 * [AppSettings.LOW_BATTERY_PERCENT], and clears it once they're charged above
 * it again. Fed by [org.opendrop.app.OpenDropApplication] with the level while
 * connected (null otherwise).
 */
class LowBatteryAlert(private val context: Context, private val settings: AppSettings) {
    private var alerted = false

    fun onLevel(name: String, level: Int?) {
        if (level == null) return
        if (level > AppSettings.LOW_BATTERY_PERCENT) {
            if (alerted) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            alerted = false
            return
        }
        if (alerted || !settings.state.value.lowBatteryAlert) return
        alerted = true
        post(name, level)
    }

    @SuppressLint("MissingPermission") // checked just below
    private fun post(name: String, level: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_opendrop)
            .setContentTitle(context.getString(R.string.notify_battery_low_title, name))
            .setContentText(context.getString(R.string.notify_battery_low_text, level))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "battery"
        private const val NOTIFICATION_ID = 2

        fun createChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_battery))
                .setDescription(context.getString(R.string.channel_battery_description))
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}
