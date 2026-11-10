package com.aimbot.runner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aimbot.runner.databinding.ActivityMainBinding
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import com.aimbot.runner.script.ScriptRunner

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var runner: ScriptRunner
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
        games = runner.loadGames()

        requestRuntimePermissions()

        binding.btnRefresh.setOnClickListener {
            games = runner.loadGames()
            bindGameList()
            binding.tvStatus.text = "Loaded ${games.size} games"
        }

        binding.btnStartFloating.setOnClickListener {
            startFloatingIcon()
        }

        binding.btnRun.setOnClickListener {
            val script = selectedScript
            val game = selectedGame
            if (script == null || game == null) {
                binding.tvStatus.text = "Select game and script first"
                return@setOnClickListener
            }
            if (ScriptService.isRunning()) {
                ScriptService.stopScript(this)
                updateRunButton(false)
                binding.tvStatus.text = "Stopped"
                return@setOnClickListener
            }

            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(dm)
            val w = dm.widthPixels
            val h = dm.heightPixels

            // The engine lives in ScriptService so it keeps running while the
            // user is inside the game.
            ensureScriptService()

            val started = ScriptService.startScript(this, game, script, w, h)
            if (started) {
                updateRunButton(true)
                binding.tvStatus.text = "Running: ${script.name}"
                startStatusPoll()
            } else {
                updateRunButton(false)
                binding.tvStatus.text = "Start failed - is ${game.name} running?"
                Toast.makeText(
                    this,
                    "${game.name} is not running, or its module is not loaded.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        bindGameList()
    }

    private fun requestRuntimePermissions() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WAKE_LOCK
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
        ensureOverlayPermission()
    }

    /** SYSTEM_ALERT_WINDOW is a special app-op, not a runtime permission. */
    private fun ensureOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun bindGameList() {
        val adapter = GameAdapter(games)
        binding.listGames.adapter = adapter
        binding.tvStatGames.text = games.size.toString()
        binding.tvStatScripts.text = runner.loadScripts().size.toString()

        binding.listGames.setOnItemClickListener { _, _, position, _ ->
            val g = games.getOrNull(position) ?: return@setOnItemClickListener
            selectedGame = g
            binding.tvSelectedGame.text = "Game: ${g.name}"
            bindScriptList(g.name)
        }
        binding.listGames.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
    }

    private fun bindScriptList(gameName: String) {
        val scripts = runner.scriptsForGame(gameName)
        val adapter = ScriptAdapter(scripts)
        binding.listScripts.adapter = adapter
        binding.listScripts.setOnItemClickListener { _, _, position, _ ->
            val s = scripts.getOrNull(position) ?: return@setOnItemClickListener
            selectedScript = s
            binding.tvSelectedScript.text = "Script: ${s.name}"
            openFeatureScreen(selectedGame!!, s)
        }
        binding.listScripts.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
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
        binding.btnRun.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (running) R.color.danger else R.color.success
        )
    }

    private fun startStatusPoll() {
        stopStatusPoll()
        val r = object : Runnable {
            override fun run() {
                if (ScriptService.isRunning()) {
                    binding.tvStatus.text = "Status: ${ScriptService.status()}"
                    binding.tvStatStatus.text = "ON"
                    binding.tvStatStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.success))
                    handler.postDelayed(this, 1000)
                } else {
                    binding.tvStatus.text = "Status: stopped"
                    binding.tvStatStatus.text = "—"
                    binding.tvStatStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.warning))
                    updateRunButton(false)
                }
            }
        }
        statusRunnable = r
        handler.postDelayed(r, 1000)
    }

    private fun stopStatusPoll() {
        val r = statusRunnable
        if (r != null) handler.removeCallbacks(r)
        statusRunnable = null
    }

    /** Starts ScriptService if it is not already up, then waits briefly for it. */
    private fun ensureScriptService() {
        if (ScriptService.get() != null) return
        val intent = Intent(this, ScriptService::class.java).setAction("STATUS")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Give onCreate() a moment to publish the instance.
        val deadline = System.currentTimeMillis() + 2000
        while (ScriptService.get() == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun startFloatingIcon() {
        ensureOverlayPermission()
        // Verify overlay permission is actually granted before starting services.
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission not granted", Toast.LENGTH_LONG).show()
            return
        }

        val icon = Intent(this, FloatingIconService::class.java).setAction("SHOW")
        val overlay = Intent(this, OverlayService::class.java).setAction("SHOW")

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(icon)
                startForegroundService(overlay)
            } else {
                startService(icon)
                startService(overlay)
            }
            binding.tvStatus.text = "Floating icon active"
        } catch (e: Exception) {
            binding.tvStatus.text = "Overlay failed: ${e.message}"
            Toast.makeText(this, "Overlay failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStatusPoll()
        // The engine belongs to ScriptService and keeps running on purpose.
    }

    private inner class GameAdapter(private val items: List<GameConfig>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_game, parent, false)
            val g = items[position]
            v.findViewById<TextView>(R.id.tv_icon).text = g.name.take(1).uppercase()
            v.findViewById<TextView>(R.id.tv_name).text = g.name
            v.findViewById<TextView>(R.id.tv_cat).text = g.category ?: "game"
            return v
        }
    }

    private inner class ScriptAdapter(private val items: List<ScriptConfig>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_script, parent, false)
            val s = items[position]
            v.findViewById<TextView>(R.id.tv_type).text = s.type.uppercase()
            v.findViewById<TextView>(R.id.tv_name).text = s.name
            return v
        }
    }
}
