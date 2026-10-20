package com.familycontrol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.familycontrol.app.MainActivity

/**
 * Usługa pierwszoplanowa (Foreground Service) dbająca o ciągłe odliczanie
 * i wyświetlanie powiadomienia pełnoekranowego (FullScreenIntent) po upływie limitu czasu.
 */
class SessionMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "family_control_timer_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_REMAINING_SECONDS = "EXTRA_REMAINING_SECONDS"

        fun start(context: Context, remainingSeconds: Long) {
            val intent = Intent(context, SessionMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SessionMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val remaining = intent?.getLongExtra(EXTRA_REMAINING_SECONDS, 0L) ?: 0L
        val notification = buildTimerNotification(remaining)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Family Control - Czas na ekranie",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Powiadomienia o bieżącym limicie czasu dla dziecka"
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildTimerNotification(remainingSeconds: Long): Notification {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tryb Dziecka aktywny")
            .setContentText("Pozostały czas: $timeFormatted")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(fullScreenPendingIntent, remainingSeconds <= 0)
            .build()
    }
}
