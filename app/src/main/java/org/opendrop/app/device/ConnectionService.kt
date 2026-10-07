package org.opendrop.app.device

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.opendrop.app.MainActivity
import org.opendrop.app.OpenDropApplication
import org.opendrop.app.R
import org.opendrop.protocol.EqPreset

/**
 * Foreground service that keeps the app process, and with it the
 * [DeviceController]'s connection, alive while the earbuds are connected or
 * reconnecting. Shows an ongoing notification with a Disconnect action and
 * stops itself when the connection ends.
 */
class ConnectionService : Service() {
    private val controller get() = (application as OpenDropApplication).controller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            // The watcher sees the connection end and stops the service.
            controller.disconnect()
            if (watcher == null) stopSelf()
            return START_NOT_STICKY
        }
        // startForegroundService() requires startForeground() even if we're about
        // to stop (the connection may already have ended), or Android crashes the app.
        val content = Content.of(controller.state.value)
        if (!showInForeground(content ?: Content.PLACEHOLDER) || content == null) {
            stop()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            watcher = scope.launch {
                // Only what the notification shows; packet log updates don't count.
                controller.state.map { Content.of(it) }.distinctUntilChanged().collect { next ->
                    if (next == null) stop() else showInForeground(next)
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Starts or updates the foreground notification. False if Android refused. */
    private fun showInForeground(content: Content): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(content), type)
        true
    } catch (e: RuntimeException) {
        // E.g. Bluetooth permission revoked while connected (Android 14 checks it).
        Log.w(TAG, "Couldn't run in the foreground", e)
        false
    }

    private fun stop() {
        watcher?.cancel()
        watcher = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(content: Content): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, ConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_opendrop)
            .setContentTitle(content.name)
            .setContentText(content.text())
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_disconnect), disconnect)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** What the notification shows; null when there's no connection to hold. */
    private data class Content(
        val name: String,
        val connection: Connection,
        val preset: String?,
        val battery: Int?,
    ) {
        fun text(): String = when (connection) {
            Connection.Connected -> listOfNotNull("Connected", preset, battery?.let { "$it %" }).joinToString(" · ")
            Connection.Reconnecting -> "Reconnecting…"
            else -> "Connecting…"
        }

        companion object {
            val PLACEHOLDER = Content("Earbuds", Connection.Connecting, preset = null, battery = null)

            fun of(state: UiState): Content? {
                if (!state.connection.isActive) return null
                return Content(
                    name = state.selected?.name ?: "Earbuds",
                    connection = state.connection,
                    preset = state.device.eqPresetId?.let(EqPreset::of)?.label,
                    battery = state.battery,
                )
            }
        }
    }

    companion object {
        private const val TAG = "ConnectionService"
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "org.opendrop.app.action.DISCONNECT"

        fun createChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_connection))
                .setDescription(context.getString(R.string.channel_connection_description))
                .setShowBadge(false)
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        /**
         * Starts the service. Android 12+ refuses foreground services started
         * from the background (e.g. auto-connect while the app isn't open); the
         * connection then still runs, just without the service's protection.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Couldn't start the connection service", e)
            }
        }
    }
}
