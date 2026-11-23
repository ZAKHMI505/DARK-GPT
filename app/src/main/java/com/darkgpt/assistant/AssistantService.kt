package com.darkgpt.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class AssistantService : Service() {

    companion object {
        const val ACTION_START = "dgpt.start"
        const val ACTION_STOP = "dgpt.stop"
        const val ACTION_WAKE = "dgpt.wake"
        const val ACTION_STOP_TALK = "dgpt.stoptalk"
        const val ACTION_REFRESH_ORB = "dgpt.orb"
        private const val CH = "darkgpt_assistant"
        private const val NID = 1

        fun start(c: Context) {
            ContextCompat.startForegroundService(c, Intent(c, AssistantService::class.java).setAction(ACTION_START))
        }

        fun send(c: Context, action: String) {
            if (Bus.running) c.startService(Intent(c, AssistantService::class.java).setAction(action))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, AssistantService::class.java))
        }
    }

    private var engine: VoiceEngine? = null
    private var battery: BatteryAssistant? = null
    private var orb: OverlayOrb? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_WAKE -> {
                engine?.wake()
                return START_STICKY
            }
            ACTION_STOP_TALK -> {
                engine?.stopTalk()
                return START_STICKY
            }
            ACTION_REFRESH_ORB -> {
                orb?.hide()
                if (Prefs.bool(this, "orb_enabled", false)) orb?.show()
                return START_STICKY
            }
        }
        if (engine == null) boot()
        return START_STICKY
    }

    private fun boot() {
        createChannel()
        try {
            ServiceCompat.startForeground(
                this, NID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            ErrorLog.add(this, "Service start failed: ${e.message}")
            stopSelf()
            return
        }
        Bus.running = true
        Bus.state = VState.IDLE
        Bus.changed()

        val eng = VoiceEngine(this)
        eng.onState = { refreshNotification() }
        engine = eng
        eng.start()

        battery = BatteryAssistant(this) { eng.announce(it) }.also { it.start() }

        orb = OverlayOrb(
            this,
            onTap = { engine?.wake() },
            onLong = {
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
        if (Prefs.bool(this, "orb_enabled", false) && Settings.canDrawOverlays(this)) orb?.show()
    }

    override fun onDestroy() {
        engine?.stop()
        battery?.stop()
        orb?.hide()
        engine = null
        battery = null
        orb = null
        Bus.running = false
        Bus.state = VState.IDLE
        Bus.hint = ""
        Bus.changed()
        super.onDestroy()
    }

    // ------------------------------------------------------------ notification
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "DARK GPT Assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun refreshNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val status = when (Bus.state) {
            VState.IDLE -> "Ready"
            VState.LISTENING -> "Listening…"
            VState.PROCESSING -> "Thinking…"
            VState.SPEAKING -> "Speaking…"
            VState.PAUSED -> "Paused (say LISTEN)"
            VState.EXECUTING -> "Executing…"
            VState.ERROR -> "Error: " + Bus.hint
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        fun act(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, AssistantService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("DARK GPT")
            .setContentText(status)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop talking", act(ACTION_STOP_TALK, 1))
            .addAction(0, "Turn off", act(ACTION_STOP, 2))
            .build()
    }
}
