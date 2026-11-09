package com.aimbot.runner

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import com.aimbot.runner.script.ScriptRunner

class ScriptService : Service() {

    private lateinit var runner: ScriptRunner

    override fun onCreate() {
        super.onCreate()
        runner = ScriptRunner(applicationContext)
        startForeground(1, buildNotification("Runner", "Background service ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "START" -> {
                val gameName = intent?.getStringExtra("game") ?: ""
                val scriptName = intent?.getStringExtra("script") ?: ""
                Log.d("ScriptService", "START $gameName / $scriptName")
                // actual start triggered from MainActivity via runner directly
            }
            "STOP" -> {
                runner.stop()
                Log.d("ScriptService", "STOP")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runner.stop()
        super.onDestroy()
    }

    private fun buildNotification(channel: String, text: String): android.app.Notification {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                android.app.NotificationChannel("runner_bg", "Runner BG", android.app.NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, "runner_bg")
            .setContentTitle(channel)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}
