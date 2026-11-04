package com.zenro.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zenro.app.MainActivity

/** Button presses from the notification are passed to whoever is listening (the ViewModel). */
object NotifActions {
    const val MUTE = "com.zenro.app.action.MUTE"
    const val STOP = "com.zenro.app.action.STOP"
    const val SCREEN_OFF = "com.zenro.app.action.SCREEN_OFF"

    @Volatile var handler: ((String) -> Unit)? = null

    fun dispatch(action: String?) {
        if (action != null) handler?.invoke(action)
    }
}

class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotifActions.dispatch(intent.action)
    }
}

/**
 * Keeps the microphone session alive while the app is in the background and shows
 * a visible notification so the mic is never used secretly.
 * The notification has two buttons: mic on/off (mute) and stop.
 */
class VoiceForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        running = true
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(this, muted, sharing), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "zenro_voice"
        private const val NOTIF_ID = 1

        @Volatile private var running = false
        @Volatile private var muted = false
        @Volatile private var sharing = false

        private fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "ZENRO voice session", NotificationManager.IMPORTANCE_LOW)
            )
        }

        private fun action(ctx: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, code,
                Intent(ctx, NotifActionReceiver::class.java).setAction(action).setPackage(ctx.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun build(ctx: Context, muted: Boolean, sharing: Boolean): Notification {
            ensureChannel(ctx)
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
            val b = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("ZENRO")
                .setContentText(
                    (if (muted) "Mic band hai (muted)" else "Mic on hai - baat chal rahi hai") +
                        (if (sharing) " | Screen live" else "")
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(0, if (muted) "Mic on karo" else "Mic band karo", action(ctx, NotifActions.MUTE, 1))
                .addAction(0, "Session band", action(ctx, NotifActions.STOP, 2))
            if (sharing) b.addAction(0, "Screen band", action(ctx, NotifActions.SCREEN_OFF, 3))
            return b.build()
        }

        fun start(ctx: Context) {
            muted = false
            sharing = false
            ContextCompat.startForegroundService(ctx, Intent(ctx, VoiceForegroundService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VoiceForegroundService::class.java))
        }

        /** Updates the notification text/button when the mute state changes. No-op if the service is not running. */
        fun setMuted(ctx: Context, value: Boolean) {
            muted = value
            refresh(ctx)
        }

        /** Shows "Screen live" and a "Screen band" button while the screen is being shared. */
        fun setSharing(ctx: Context, value: Boolean) {
            sharing = value
            refresh(ctx)
        }

        private fun refresh(ctx: Context) {
            if (!running) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, build(ctx, muted, sharing))
        }
    }
}
