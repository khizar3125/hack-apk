package com.aimbot.runner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aimbot.runner.databinding.ActivityMainBinding
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import com.aimbot.runner.root.ScreenCaptureService
import com.aimbot.runner.script.ScriptRunner
import com.aimbot.runner.scanner.AutoConfigActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var runner: ScriptRunner
    private lateinit var games: List<GameConfig>
    private var selectedGame: GameConfig? = null
    private var selectedScript: ScriptConfig? = null
    private val handler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null

    /** True = memory-based scripts (requires root). False = accessibility only (no root). */
    private var nonRootMode: Boolean = false

    /** Request codes. */
    private val RC_OVERLAY = 1001
    private val RC_ACCESSIBILITY = 1002
    private val RC_MEDIA_PROJECTION = 1003
    private val RC_PERMS = 1004

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        runner = ScriptRunner(this)
        games = runner.loadGames()

        requestRuntimePermissions()

        // Root / non-root mode toggle.
        binding.switchNonRoot.setOnCheckedChangeListener { _, isChecked ->
            nonRootMode = isChecked
            binding.tvModeNote.text = if (isChecked) {
                "Non-root mode: memory features disabled. Auto-tap / auto-fire / auto-play work via AccessibilityService."
            } else {
                "Root mode: memory read/write enabled. Root access required."
            }
            binding.tvModeNote.setTextColor(
                ContextCompat.getColor(this, if (isChecked) R.color.warning else R.color.success)
            )
        }

        binding.btnRefresh.setOnClickListener {
            games = runner.loadGames()
            bindGameList()
            binding.tvStatus.text = "Loaded ${games.size} games"
        }

        binding.btnAutoConfig.setOnClickListener {
            val intent = Intent(this, AutoConfigActivity::class.java)
            startActivity(intent)
        }

        binding.btnStartFloating.setOnClickListener {
            startFloatingIcon()
        }

        binding.btnRequestAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnRequestCapture.setOnClickListener {
            requestMediaProjection()
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

            if (nonRootMode) {
                startNonRoot(game, script, w, h)
            } else {
                ensureScriptService()
                val started = ScriptService.startScript(this, game, script, w, h)
                if (started) {
                    updateRunButton(true)
                    binding.tvStatus.text = "Running: ${script.name}"
                    startStatusPoll()
                } else {
                    updateRunButton(false)
                    binding.tvStatus.text = "Start failed - root or game not running?"
                    Toast.makeText(
                        this,
                        "${game.name} is not running, or root is not granted.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        bindGameList()
    }

    /** Start script in non-root mode (accessibility input only, no memory). */
    private fun startNonRoot(game: GameConfig, script: ScriptConfig, screenW: Int, screenH: Int) {
        // Need AccessibilityService enabled for non-root input.
        val am = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        if (!am.isEnabled) {
            binding.tvStatus.text = "AccessibilityService not enabled for non-root mode"
            Toast.makeText(this, "Enable AccessibilityService for Runner first", Toast.LENGTH_LONG).show()
            return
        }

        ScriptService.startNonRootScript(this, game, script, screenW, screenH)
        updateRunButton(true)
        binding.tvStatus.text = "Running (non-root): ${script.name}"
        startStatusPoll()
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
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), RC_PERMS)
        }
        ensureOverlayPermission()
    }

    private fun ensureOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, RC_OVERLAY)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestMediaProjection() {
        val mgr = getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mgr.createScreenCaptureIntent()
        startActivityForResult(intent, RC_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val svc = ScreenCaptureService() // not used directly; start via intent
                val intent = Intent(this, com.aimbot.runner.root.ScreenCaptureService::class.java)
                // Pass result via a static holder so the service can pick it up.
                com.aimbot.runner.ScreenCaptureHolder.resultCode = resultCode
                com.aimbot.runner.ScreenCaptureHolder.resultData = data
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                binding.tvStatus.text = "Screen capture started (non-root ESP source)"
            } else {
                binding.tvStatus.text = "Screen capture permission denied"
            }
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
        intent.putExtra("non_root_mode", nonRootMode)
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

/** Holds MediaProjection result data until ScreenCaptureService reads it. */
object ScreenCaptureHolder {
    var resultCode: Int = Activity.RESULT_CANCELED
    var resultData: Intent? = null
}
