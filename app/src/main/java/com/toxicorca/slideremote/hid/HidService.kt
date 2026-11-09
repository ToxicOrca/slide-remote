package com.toxicorca.slideremote.hid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.toxicorca.slideremote.MainActivity
import com.toxicorca.slideremote.R
import com.toxicorca.slideremote.app

/**
 * Foreground service that keeps the app process (and therefore the HID registration)
 * alive while a computer is connected, even with the screen off mid-presentation.
 *
 * Android crashes the app if a service started with startForegroundService() is stopped
 * before it calls startForeground(). So this service always calls startForeground() first,
 * and stopping is done by sending it a STOP intent rather than stopService().
 */
class HidService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val status = applicationContext.app.hid.status.value
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("Connected to ${status.hostName ?: "computer"}")
            .setContentText("Your phone is acting as a keyboard for the presentation.")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            running = true
            // Cleared only once running is set, so a stop() in between is never skipped.
            startPending = false
        } catch (_: Throwable) {
            running = false
            startPending = false
            stopSelf()
            return START_NOT_STICKY
        }
        // Decide from the live state only: a STOP queued before a quick reconnect must not win.
        if (!applicationContext.app.hid.status.value.isConnected) stopNow(startId)
        return START_NOT_STICKY
    }

    private fun stopNow(startId: Int) {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
        running = false
        // With a start id, a newer queued start keeps the service alive instead of crashing.
        stopSelf(startId)
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Keyboard connection", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "hid_connection"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_STOP = "com.toxicorca.slideremote.STOP_HID_SERVICE"

        @Volatile private var running = false
        @Volatile private var startPending = false

        /** Safe to call while running: it just refreshes the notification. */
        fun start(context: Context) {
            try {
                startPending = true
                ContextCompat.startForegroundService(context, Intent(context, HidService::class.java))
            } catch (_: Throwable) {
                // Android 12+ blocks starting from the background; the link still works
                // while the app is open, so this is non-fatal.
                startPending = false
            }
        }

        fun stop(context: Context) {
            if (!running && !startPending) return
            try {
                context.startService(Intent(context, HidService::class.java).setAction(ACTION_STOP))
            } catch (_: Throwable) {
            }
        }
    }
}
