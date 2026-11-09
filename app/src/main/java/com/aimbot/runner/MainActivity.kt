package com.aimbot.runner

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aimbot.runner.databinding.ActivityMainBinding
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import com.aimbot.runner.script.ScriptExecutor
import com.aimbot.runner.script.ScriptRunner

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var runner: ScriptRunner
    private lateinit var executor: ScriptExecutor
    private lateinit var games: List<GameConfig>
    private var selectedGame: GameConfig? = null
    private var selectedScript: ScriptConfig? = null
    private val handler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        runner = ScriptRunner(this)
        executor = ScriptExecutor(this, runner)
        games = runner.loadGames()

        requestPermissions()

        binding.btnRefresh.setOnClickListener {
            games = runner.loadGames()
            bindGameList()
        }

        binding.btnStartFloating.setOnClickListener {
            startFloatingIcon()
        }

        binding.btnRun.setOnClickListener {
            if (selectedScript == null || selectedGame == null) {
                binding.tvStatus.text = "Select game and script first"
                return@setOnClickListener
            }
            if (executor.isActive(selectedScript!!, selectedGame!!)) {
                executor.stop(selectedScript!!, selectedGame!!)
                updateRunButton(false)
                binding.tvStatus.text = "Stopped"
            } else {
                val script = selectedScript!!
                val game = selectedGame!!
                val dm = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(dm)
                val w = dm.widthPixels
                val h = dm.heightPixels
                if (executor.execute(game, script, w, h)) {
                    updateRunButton(true)
                    binding.tvStatus.text = "Running: ${script.name}"
                    startStatusPoll()
                } else {
                    binding.tvStatus.text = "Failed to start"
                }
            }
        }

        bindGameList()
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.FOREGROUND_SERVICE
        )
        ActivityCompat.requestPermissions(this, perms, 1001)

        // SYSTEM_ALERT_WINDOW cannot be requested via ActivityCompat.
        // Check and open overlay settings if not granted.
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun bindGameList() {
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            games.map { it.name }.toTypedArray())
        binding.listGames.adapter = adapter
        binding.listGames.setOnItemClickListener { _, _, position, _ ->
            selectedGame = games[position]
            binding.tvSelectedGame.text = "Game: ${selectedGame!!.name}"
            bindScriptList(selectedGame!!.name)
        }
    }

    private fun bindScriptList(gameName: String) {
        val scripts = runner.scriptsForGame(gameName)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            scripts.map { it.name + " (" + it.type + ")" }.toTypedArray())
        binding.listScripts.adapter = adapter
        binding.listScripts.setOnItemClickListener { _, _, position, _ ->
            selectedScript = scripts[position]
            binding.tvSelectedScript.text = "Script: ${selectedScript!!.name}"
            openFeatureScreen(selectedGame!!, selectedScript!!)
        }
    }

    private fun openFeatureScreen(game: GameConfig, script: ScriptConfig) {
        val intent = Intent(this, GameScreenActivity::class.java)
        intent.putExtra("game_name", game.name)
        intent.putExtra("game_package", game.packageName)
        intent.putExtra("game_module", game.module)
        intent.putExtra("script_name", script.name)
        intent.putExtra("script_type", script.type)
        intent.putExtra("script_config", script.config?.toString() ?: "{}")
        startActivity(intent)
    }

    private fun updateRunButton(running: Boolean) {
        binding.btnRun.text = if (running) "Stop" else "Run"
        binding.btnRun.setBackgroundColor(
            if (running) ContextCompat.getColor(this, android.R.color.holo_red_light)
            else ContextCompat.getColor(this, android.R.color.holo_green_light)
        )
    }

    private fun startStatusPoll() {
        statusRunnable = object : Runnable {
            override fun run() {
                if (runner.isRunning()) {
                    binding.tvStatus.text = "Status: ${runner.status()}"
                    handler.postDelayed(this, 1000)
                } else {
                    binding.tvStatus.text = "Status: stopped"
                    updateRunButton(false)
                }
            }
        }
        val r = statusRunnable
        if (r != null) handler.postDelayed(r, 1000)
    }

    private fun startFloatingIcon() {
        val intent = Intent(this, FloatingIconService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Also start overlay service for ESP/crosshair
        val overlayIntent = Intent(this, OverlayService::class.java)
        overlayIntent.action = "SHOW"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val r = statusRunnable
        if (r != null) handler.removeCallbacks(r)
        runner.stop()
    }
}
