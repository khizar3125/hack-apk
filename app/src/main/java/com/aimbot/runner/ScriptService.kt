package com.aimbot.runner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import com.aimbot.runner.script.ScriptExecutor
import com.aimbot.runner.script.ScriptRunner

/**
 * Foreground service that owns the script engine so it keeps running while
 * the user is inside the game.
 *
 * MainActivity and the floating menu both talk to this single instance
 * through the companion helpers, so there is exactly one native engine.
 */
class ScriptService : Service() {

    companion object {
        private const val CHANNEL_ID = "runner_bg"
        private const val NOTIF_ID = 4710

        @Volatile
        private var instance: ScriptService? = null

        @Volatile
        private var lastError: String = ""

        fun get(): ScriptService? = instance

        /** Human readable reason for the last failed start. */
        fun lastError(): String = lastError

        /** Starts the engine for [game] + [script]. Returns false if the target game is not running. */
        fun startScript(context: Context, game: GameConfig, script: ScriptConfig, screenW: Int, screenH: Int): Boolean {
            val svc = instance
            if (svc == null) {
                lastError = "Script service is not running yet."
                Log.e("ScriptService", "not started")
                return false
            }
            val ok = svc.executor.execute(game, script, screenW, screenH)
            if (!ok) {
                lastError = svc.executor.lastError
            } else {
                lastError = ""
            }
            return ok
        }

        fun stopScript(context: Context) {
            instance?.runner?.stop()
            OverlayService.instance?.setTarget(0f, 0f, false)
        }

        fun isRunning(): Boolean = instance?.runner?.isRunning() ?: false

        fun status(): String = instance?.runner?.status() ?: "stopped"
    }

    lateinit var runner: ScriptRunner
        private set
    lateinit var executor: ScriptExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        runner = ScriptRunner(applicationContext)
        executor = ScriptExecutor(applicationContext, runner)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Runner", "Ready"))
        // Publish only once everything is initialized so callers never see a
        // half-built service.
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                runner.stop()
                OverlayService.instance?.setTarget(0f, 0f, false)
                Log.i("ScriptService", "STOP")
            }
            "STATUS" -> {
                Log.i("ScriptService", "status ${runner.status()}")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runner.stop()
        instance = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Runner BG", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
        return builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }
}
