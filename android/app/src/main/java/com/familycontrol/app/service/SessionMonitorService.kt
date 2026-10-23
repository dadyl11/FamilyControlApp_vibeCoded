package com.familycontrol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.familycontrol.app.MainActivity
import com.familycontrol.app.domain.SessionManager
import com.familycontrol.app.model.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Usługa pierwszoplanowa (Foreground Service) dbająca o ciągłe odliczanie czasu
 * podczas gdy dziecko gra w gry, wygłaszanie komunikatu głosowego 3 minuty przed końcem,
 * pokazywanie popupu z ostrzeżeniem oraz natychmiastowe zablokowanie ekranu po limicie.
 */
class SessionMonitorService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val TIMER_CHANNEL_ID = "family_control_timer_channel"
        const val WARNING_CHANNEL_ID = "family_control_warning_channel"
        const val LOCK_CHANNEL_ID = "family_control_lock_channel"
        const val NOTIFICATION_ID = 1001
        const val WARNING_NOTIFICATION_ID = 1002
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, SessionMonitorService::class.java).apply {
                action = ACTION_START
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

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var timerJob: Job? = null
    private var hasTriggeredLockoutAlert = false
    private var hasTriggeredThreeMinuteWarning = false

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate() {
        super.onCreate()
        SessionManager.init(applicationContext)
        createNotificationChannels()
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val result = textToSpeech?.setLanguage(Locale("pl", "PL"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.language = Locale.getDefault()
                }
                isTtsReady = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            timerJob?.cancel()
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return Service.START_NOT_STICKY
        }

        // Natychmiastowe pokazanie notyfikacji pierwszoplanowej
        val initialNotification = buildTimerNotification(SessionManager.remainingSeconds.value, false)
        startForeground(NOTIFICATION_ID, initialNotification)

        startBackgroundTimerLoop()
        return Service.START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timerJob?.cancel()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun startBackgroundTimerLoop() {
        if (timerJob?.isActive == true) return

        timerJob = serviceScope.launch {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            while (isActive) {
                delay(1000L)

                val isScreenInteractive = powerManager.isInteractive
                val currentState = SessionManager.currentState.value

                // Odliczaj tylko, gdy ekran jest włączony i nie jesteśmy w trybie konfiguracji rodzica
                if (isScreenInteractive && currentState != SessionState.PARENT_MODE) {
                    val newState = SessionManager.tick()
                    val remaining = SessionManager.remainingSeconds.value

                    // Sprawdzenie progu 3 minut (180 sekund)
                    if (remaining in 1L..180L && !hasTriggeredThreeMinuteWarning) {
                        hasTriggeredThreeMinuteWarning = true
                        triggerThreeMinuteWarning()
                    } else if (remaining > 180L) {
                        hasTriggeredThreeMinuteWarning = false
                    }

                    if (newState == SessionState.TIME_EXPIRED) {
                        enforceTimeExpiredLockout()
                    } else {
                        hasTriggeredLockoutAlert = false
                        val notification = buildTimerNotification(remaining, newState == SessionState.WARNING)
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    }
                } else if (currentState == SessionState.TIME_EXPIRED) {
                    // Jeśli limit został wyczerpany, pilnuj aby dziecko nie grało dalej
                    enforceTimeExpiredLockout()
                }
            }
        }
    }

    /**
     * Wygłoszenie komunikatu głosowego oraz wyświetlenie baneru i popupu 3 minuty przed końcem.
     */
    private fun triggerThreeMinuteWarning() {
        // 1. Komunikat głosowy przez syntezator mowy w języku polskim
        speakVoiceAlert("Uwaga! Za trzy minuty koniec gry. Dokończ poziom i zapisz stan.")

        // 2. Wibracja ostrzegawcza (podwójny impuls)
        vibrateWarning()

        // 3. Systemowy Popup (Toast) widoczny bezpośrednio nad każdą grą
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "⚠️ UWAGA: Za 3 minuty koniec gry! Dokończ zadanie i zapisz stan gry.",
                Toast.LENGTH_LONG
            ).show()
        }

        // 4. Baner powiadomienia typu Heads-up (opada na ekran gry)
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val warningNotification = buildWarningBannerNotification()
            notificationManager.notify(WARNING_NOTIFICATION_ID, warningNotification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun speakVoiceAlert(text: String) {
        if (!isTtsReady || textToSpeech == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fc_warning_tts")
            } else {
                @Suppress("DEPRECATION")
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrateWarning() {
        try {
            val pattern = longArrayOf(0, 250, 150, 250)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Wymuszenie blokady ekranu: wywołanie pełnoekranowego okna blokady nad uruchomioną grą.
     */
    private fun enforceTimeExpiredLockout() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val lockNotification = buildLockoutNotification()
        notificationManager.notify(NOTIFICATION_ID, lockNotification)

        if (!hasTriggeredLockoutAlert) {
            hasTriggeredLockoutAlert = true
            vibrateAlert()
            speakVoiceAlert("Koniec czasu na gry! Limit na dziś dobiegł końca.")
        }

        // Jeśli MainActivity nie znajduje się na pierwszym planie (np. dziecko jest w grze lub próbuje wrócić do gry)
        if (!SessionManager.isActivityResumed) {
            launchLockoutActivity()
        }
    }

    private fun launchLockoutActivity() {
        try {
            val lockIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_FORCE_TIME_EXPIRED", true)
            }
            startActivity(lockIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrateAlert() {
        try {
            val pattern = longArrayOf(0, 400, 200, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Kanał bieżącego timera
            val timerChannel = NotificationChannel(
                TIMER_CHANNEL_ID,
                "Family Control - Licznik czasu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Wyświetla aktualny czas pozostały dla dziecka"
                setShowBadge(false)
            }
            manager.createNotificationChannel(timerChannel)

            // 2. Kanał ostrzeżenia 3 minuty przed końcem (High Importance z dźwiękiem/banerem)
            val warningChannel = NotificationChannel(
                WARNING_CHANNEL_ID,
                "Family Control - Ostrzeżenie 3 minuty",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wyświetla baner i powiadomienie 3 minuty przed końcem limitu"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            manager.createNotificationChannel(warningChannel)

            // 3. Kanał blokady pełnoekranowej (High Importance z dźwiękiem i wibracją)
            val lockChannel = NotificationChannel(
                LOCK_CHANNEL_ID,
                "Family Control - Koniec czasu i Blokada",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wymusza natychmiastowe zablokowanie gry po upływie limitu"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(lockChannel)
        }
    }

    private fun buildTimerNotification(remainingSeconds: Long, isWarning: Boolean): Notification {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isWarning) "⚠️ Uwaga: Czas zaraz się skończy!" else "Kącik Dziecka aktywny"
        val content = "Pozostały czas na gry: $timeFormatted"

        return NotificationCompat.Builder(this, TIMER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildWarningBannerNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            102,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setContentTitle("⚠️ Za 3 minuty koniec gry!")
            .setContentText("Dokończ poziom i zapisz stan gry. Zostały 3 minuty.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .build()
    }

    private fun buildLockoutNotification(): Notification {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_FORCE_TIME_EXPIRED", true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, LOCK_CHANNEL_ID)
            .setContentTitle("⛔ Koniec czasu na gry!")
            .setContentText("Limit na dziś dobiegł końca. Wymagany PIN rodzica do odblokowania.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}

