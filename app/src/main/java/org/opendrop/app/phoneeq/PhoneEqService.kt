package org.opendrop.app.phoneeq

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
import kotlinx.coroutines.launch
import org.opendrop.app.MainActivity
import org.opendrop.app.OpenDropApplication
import org.opendrop.app.R

/**
 * Holds the phone EQ effect while it's on. Audio effects die with their
 * process, so this keeps the process alive with an ongoing notification (with
 * a Turn off action), and stops itself when the EQ is turned off.
 */
class PhoneEqService : Service() {
    private val phoneEq get() = (application as OpenDropApplication).phoneEq
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var engine: PhoneEqEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TURN_OFF) phoneEq.update { it.copy(enabled = false) }
        // startForegroundService() requires startForeground() even when we stop right away.
        if (!showInForeground() || !phoneEq.state.value.enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            stop()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            val eq = PhoneEqEngine().also { engine = it }
            watcher = scope.launch {
                phoneEq.state.collect { state ->
                    if (!state.enabled) {
                        stop()
                    } else {
                        phoneEq.failed.value = !eq.apply(state.curve)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun showInForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't run in the foreground", e)
        false
    }

    private fun stop() {
        watcher?.cancel()
        watcher = null
        engine?.release()
        engine = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        engine?.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val turnOff = PendingIntent.getService(
            this,
            1,
            Intent(this, PhoneEqService::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_opendrop)
            .setContentTitle(getString(R.string.phone_eq_on))
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_turn_off), turnOff)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "PhoneEqService"
        private const val CHANNEL_ID = "phone_eq"
        private const val NOTIFICATION_ID = 3
        private const val ACTION_TURN_OFF = "org.opendrop.app.action.PHONE_EQ_OFF"

        fun createChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_phone_eq))
                .setDescription(context.getString(R.string.channel_phone_eq_description))
                .setShowBadge(false)
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PhoneEqService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Couldn't start the phone EQ service", e)
            }
        }
    }
}
