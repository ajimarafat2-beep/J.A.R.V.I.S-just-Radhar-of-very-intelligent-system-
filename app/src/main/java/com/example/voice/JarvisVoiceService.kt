package com.example.voice

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

/**
 * Foreground service that owns the wake-word listener so "Hey Jarvis" keeps
 * working while the app is in the background or the phone is locked.
 *
 * The JarvisVoiceEngine lives here (not in the Activity-scoped ViewModel).
 * The ViewModel binds to this service and drives the same engine instance,
 * so every existing call site keeps working unchanged.
 *
 * While wake word is on, Android shows one quiet, low-priority notification
 * ("Jarvis is listening") — that is the closest Android allows to invisible.
 * If the wake word fires while nothing is bound (app swiped away), the
 * service opens MainActivity so the live session can start.
 */
class JarvisVoiceService : Service() {

    interface VoiceCallbacks {
        fun onTranscriptReady(text: String)
        fun onWakeTriggered()
        fun onVoiceError(message: String)
    }

    inner class VoiceBinder : Binder() {
        fun getService(): JarvisVoiceService = this@JarvisVoiceService
    }

    private val binder = VoiceBinder()
    private var engine: JarvisVoiceEngine? = null
    private var callbacks: VoiceCallbacks? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        engine = JarvisVoiceEngine(
            context = applicationContext,
            onTranscriptReady = { text -> callbacks?.onTranscriptReady(text) },
            onWakeTriggered = {
                val cb = callbacks
                if (cb != null) {
                    cb.onWakeTriggered()
                } else {
                    // Nobody is bound (app swiped away / locked) — bring Jarvis forward.
                    val open = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(EXTRA_WAKE_TRIGGERED, true)
                    }
                    runCatching { startActivity(open) }
                }
            },
            onVoiceError = { message -> callbacks?.onVoiceError(message) }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_VOICE_ID,
                    buildOngoingNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIF_VOICE_ID, buildOngoingNotification())
            }
            true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                false
            } else if (e is SecurityException || e is IllegalStateException) {
                false
            } else {
                false
            }
        }
        if (!started) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START_WAKE -> engine?.startWakeWordListener()
            ACTION_STOP -> {
                engine?.stopAllListening()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        callbacks = null
        return false
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        super.onDestroy()
    }

    fun setCallbacks(cb: VoiceCallbacks?) {
        callbacks = cb
    }

    fun getEngine(): JarvisVoiceEngine? = engine

    fun startWakeListener() {
        engine?.startWakeWordListener()
    }

    fun stopWakeListener() {
        engine?.stopWakeWordListener()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_VOICE,
                "J.A.R.V.I.S Voice — Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quiet notice while Jarvis listens for its wake word."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, JarvisVoiceService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_VOICE)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis is listening")
            .setContentText("Say \"Jarvis\" to wake — tap to open.")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START_WAKE = "com.example.voice.ACTION_START_WAKE"
        const val ACTION_STOP = "com.example.voice.ACTION_STOP"
        const val EXTRA_WAKE_TRIGGERED = "com.example.voice.EXTRA_WAKE_TRIGGERED"
        private const val CHANNEL_VOICE = "jarvis_voice_listening"
        private const val NOTIF_VOICE_ID = 2001

        fun startWakeService(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_START_WAKE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopVoiceService(context: Context) {
            context.stopService(Intent(context, JarvisVoiceService::class.java))
        }

        fun hasMicPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
