package com.aimbot.runner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private var games: List<GameConfig> = emptyList()
    private var currentScripts: List<ScriptConfig> = emptyList()
    private var selectedGame: GameConfig? = null
    private var selectedScript: ScriptConfig? = null
    private val handler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null

    /** True = non-root gestures mode. False = root memory mode. */
    private var nonRootMode: Boolean = false

    /** Active tab: 0 = Games, 1 = Scripts */
    private var currentTab: Int = 0

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

        // Pre-start ScriptService so it's ready when user runs scripts
        startScriptServiceDirectly()

        requestRuntimePermissions()
        setupListeners()
        bindGameList()
        showTab(0)

        // Select first game if available
        if (games.isNotEmpty()) {
            selectGame(games[0])
        }
    }

    private fun setupListeners() {
        // Tab switching
        binding.btnTabGames.setOnClickListener { showTab(0) }
        binding.btnTabScripts.setOnClickListener { showTab(1) }

        // Root / non-root mode toggle.
        binding.switchNonRoot.setOnCheckedChangeListener { _, isChecked ->
            nonRootMode = isChecked
            binding.tvModeNote.text = if (isChecked) {
                "Non-root mode: memory features disabled. Auto-tap & auto-fire run via Accessibility."
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
            selectedGame?.let { g ->
                bindScriptList(g.name)
            }
            binding.tvStatus.text = "Loaded ${games.size} games"
        }

        binding.btnAutoConfig.setOnClickListener {
            val intent = Intent(this, AutoConfigActivity::class.java)
            startActivity(intent)
        }

        binding.btnStartFloating.setOnClickListener {
            startFloatingIcon(silent = false)
        }

        binding.btnRequestAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnRequestCapture.setOnClickListener {
            requestMediaProjection()
        }

        binding.btnRun.setOnClickListener {
            handleRunOrStop()
        }
    }

    /** Switches between Games and Scripts list view smoothly. */
    private fun showTab(tabIndex: Int) {
        currentTab = tabIndex
        if (tabIndex == 0) {
            binding.listGames.visibility = View.VISIBLE
            binding.listScripts.visibility = View.GONE
            binding.btnTabGames.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
            binding.btnTabGames.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent)
            binding.btnTabScripts.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnTabScripts.backgroundTintList = ContextCompat.getColorStateList(this, R.color.bg_card)
        } else {
            binding.listGames.visibility = View.GONE
            binding.listScripts.visibility = View.VISIBLE
            binding.btnTabScripts.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
            binding.btnTabScripts.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent)
            binding.btnTabGames.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnTabGames.backgroundTintList = ContextCompat.getColorStateList(this, R.color.bg_card)
        }
    }

    /** Checks whether the target game's package is installed on this device. */
    private fun isGameInstalled(packageName: String): Boolean {
        // Method 1: Check launch intent
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) return true

        // Method 2: Check package info
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Main action: stops if already running, or checks game installation & auto-launches game before starting script. */
    private fun handleRunOrStop() {
        if (ScriptService.isRunning()) {
            ScriptService.stopScript(this)
            updateRunButton(false)
            binding.tvStatus.text = "Stopped"
            binding.tvStatStatus.text = "STANDBY"
            binding.tvStatStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
            return
        }

        val game = selectedGame
        val script = selectedScript
        if (game == null) {
            binding.tvStatus.text = "Please select a game first"
            showTab(0)
            return
        }
        if (script == null) {
            binding.tvStatus.text = "Please select a script for ${game.name}"
            showTab(1)
            return
        }

        // STEP 1: Check if the game is installed on the phone!
        val installed = isGameInstalled(game.packageName)
        if (!installed) {
            binding.tvStatus.text = "Game not installed: ${game.name}"
            showGameNotInstalledDialog(game)
            return
        }

        // STEP 2: Game is installed! Automatically launch the game.
        binding.tvStatus.text = "Launching ${game.name} & starting script..."
        appendLog("Checking game: ${game.packageName} -> INSTALLED")
        appendLog("Auto-launching ${game.name}...")

        val launchIntent = packageManager.getLaunchIntentForPackage(game.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to launch game ${game.packageName}", e)
                appendLog("Launch error: ${e.message}")
            }
        }

        // STEP 3: Auto-start Floating Menu Overlay so controls are available over the game!
        startFloatingIcon(silent = true)

        // STEP 4: Start script engine with auto-attach retry logic
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(dm)
        val w = dm.widthPixels
        val h = dm.heightPixels

        ensureScriptServiceAsync {
            if (nonRootMode) {
                // Non-root mode: give game 1.5 seconds to open foreground window, then start gestures
                handler.postDelayed({
                    val started = ScriptService.startNonRootScript(this, game, script, w, h)
                    if (started) {
                        updateRunButton(true)
                        binding.tvStatus.text = "Running (non-root): ${script.name}"
                        appendLog("Non-root script active: ${script.name}")
                        startStatusPoll()
                    } else {
                        updateRunButton(false)
                        val err = ScriptService.lastError()
                        binding.tvStatus.text = "Start failed: $err"
                        appendLog("Start failed: $err")
                    }
                }, 1500)
            } else {
                // Root mode: runs in background thread because ScriptRunner.start waits up to 12s for process/module
                appendLog("Waiting for game process & module...")
                Thread {
                    val started = ScriptService.startScript(this, game, script, w, h)
                    handler.post {
                        if (started) {
                            updateRunButton(true)
                            binding.tvStatus.text = "Running: ${script.name}"
                            appendLog("Script running attached: ${script.name}")
                            startStatusPoll()
                        } else {
                            updateRunButton(false)
                            val err = ScriptService.lastError()
                            binding.tvStatus.text = "Start failed: $err"
                            appendLog("Start failed: $err")
                            Toast.makeText(
                                this,
                                err.ifEmpty { "${game.name} process not found or root denied" },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
            }
        }
    }

    private fun showGameNotInstalledDialog(game: GameConfig) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_not_installed_title))
            .setMessage(getString(R.string.dialog_not_installed_msg, game.name, game.packageName))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .setNeutralButton("Open Play Store") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${game.packageName}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=${game.packageName}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            }
            .show()
    }

    private fun selectGame(g: GameConfig) {
        selectedGame = g
        val isInstalled = isGameInstalled(g.packageName)

        binding.tvSelectedGame.text = "🎮 ${g.name}"
        if (isInstalled) {
            binding.tvGameInstallBadge.text = "● Installed"
            binding.tvGameInstallBadge.setTextColor(ContextCompat.getColor(this, R.color.installed_green))
            binding.tvGameInstallBadge.setBackgroundResource(R.drawable.bg_badge_installed)
        } else {
            binding.tvGameInstallBadge.text = "○ Not Installed"
            binding.tvGameInstallBadge.setTextColor(ContextCompat.getColor(this, R.color.not_installed_text))
            binding.tvGameInstallBadge.setBackgroundResource(R.drawable.bg_badge_not_installed)
        }

        bindScriptList(g.name)
        (binding.listGames.adapter as? BaseAdapter)?.notifyDataSetChanged()
    }

    private fun bindGameList() {
        val adapter = GameAdapter(games)
        binding.listGames.adapter = adapter
        binding.tvStatGames.text = games.size.toString()
        binding.tvStatScripts.text = runner.loadScripts().size.toString()

        binding.listGames.setOnItemClickListener { _, _, position, _ ->
            val g = games.getOrNull(position) ?: return@setOnItemClickListener
            selectGame(g)
            // Switch to scripts tab so user sees options immediately
            showTab(1)
        }
        binding.listGames.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
    }

    private fun bindScriptList(gameName: String) {
        currentScripts = runner.scriptsForGame(gameName)
        val adapter = ScriptAdapter(currentScripts)
        binding.listScripts.adapter = adapter

        // Auto-select first script if available
        if (currentScripts.isNotEmpty()) {
            selectedScript = currentScripts[0]
            binding.tvSelectedScript.text = "⚡ Script: ${selectedScript!!.name}"
        } else {
            selectedScript = null
            binding.tvSelectedScript.text = "⚡ No scripts found"
        }

        binding.listScripts.setOnItemClickListener { _, _, position, _ ->
            val s = currentScripts.getOrNull(position) ?: return@setOnItemClickListener
            selectedScript = s
            binding.tvSelectedScript.text = "⚡ Script: ${s.name}"
            (binding.listScripts.adapter as? BaseAdapter)?.notifyDataSetChanged()
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
        binding.btnRun.text = if (running) "🛑 STOP SCRIPT" else "🚀 LAUNCH GAME & RUN"
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
                    binding.tvStatStatus.text = "ACTIVE"
                    binding.tvStatStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.success))
                    handler.postDelayed(this, 1000)
                } else {
                    binding.tvStatus.text = "Status: stopped"
                    binding.tvStatStatus.text = "STANDBY"
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

    private fun startScriptServiceDirectly() {
        val intent = Intent(this, ScriptService::class.java).setAction("STATUS")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed starting ScriptService directly", e)
        }
    }

    /** Starts ScriptService non-blockingly without freezing the main thread. */
    private fun ensureScriptServiceAsync(onReady: () -> Unit) {
        if (ScriptService.get() != null) {
            onReady()
            return
        }
        startScriptServiceDirectly()
        var count = 0
        val poll = object : Runnable {
            override fun run() {
                if (ScriptService.get() != null) {
                    onReady()
                } else if (count < 25) {
                    count++
                    handler.postDelayed(this, 100)
                } else {
                    binding.tvStatus.text = "Script service startup timeout"
                    appendLog("ScriptService startup timed out")
                }
            }
        }
        handler.postDelayed(poll, 100)
    }

    private fun startFloatingIcon(silent: Boolean = false) {
        ensureOverlayPermission()
        if (!Settings.canDrawOverlays(this)) {
            if (!silent) {
                Toast.makeText(this, "Overlay permission not granted", Toast.LENGTH_LONG).show()
            }
            return
        }

        val icon = Intent(this, FloatingIconService::class.java).setAction("SHOW")
        val overlay = Intent(this, OverlayService::class.java).setAction("SHOW")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(icon)
                startForegroundService(overlay)
            } else {
                startService(icon)
                startService(overlay)
            }
            if (!silent) {
                binding.tvStatus.text = "Floating icon & overlay active"
            }
        } catch (e: Exception) {
            if (!silent) {
                binding.tvStatus.text = "Overlay failed: ${e.message}"
                Toast.makeText(this, "Overlay failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
                val intent = Intent(this, com.aimbot.runner.root.ScreenCaptureService::class.java)
                ScreenCaptureHolder.resultCode = resultCode
                ScreenCaptureHolder.resultData = data
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                binding.tvStatus.text = "Screen capture started (non-root ESP source)"
                appendLog("Screen capture service activated")
            } else {
                binding.tvStatus.text = "Screen capture permission denied"
            }
        }
    }

    private fun appendLog(msg: String) {
        val current = binding.log.text.toString()
        val line = "[${System.currentTimeMillis() % 100000}] $msg\n"
        binding.log.text = line + current
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

            val tvIcon = v.findViewById<TextView>(R.id.tv_icon)
            val tvName = v.findViewById<TextView>(R.id.tv_name)
            val tvPackage = v.findViewById<TextView>(R.id.tv_package)
            val tvCat = v.findViewById<TextView>(R.id.tv_cat)
            val tvBadge = v.findViewById<TextView>(R.id.tv_installed_badge)

            tvIcon.text = g.name.take(1).uppercase()
            tvName.text = g.name
            tvPackage.text = g.packageName
            tvCat.text = (g.category ?: "GAME").uppercase()

            val installed = isGameInstalled(g.packageName)
            if (installed) {
                tvBadge.text = "● Installed"
                tvBadge.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.installed_green))
                tvBadge.setBackgroundResource(R.drawable.bg_badge_installed)
            } else {
                tvBadge.text = "○ Not Installed"
                tvBadge.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.not_installed_text))
                tvBadge.setBackgroundResource(R.drawable.bg_badge_not_installed)
            }

            val isSelected = selectedGame?.packageName == g.packageName
            v.setBackgroundResource(if (isSelected) R.drawable.bg_card_selected else R.drawable.bg_card)

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

            val tvType = v.findViewById<TextView>(R.id.tv_type)
            val tvName = v.findViewById<TextView>(R.id.tv_name)
            val tvDesc = v.findViewById<TextView>(R.id.tv_desc)

            tvType.text = s.type.uppercase()
            tvName.text = s.name
            tvDesc.text = when (s.type.lowercase()) {
                "aimbot" -> "Auto-aim, FOV control, bone tracking"
                "esp" -> "Player boxes, skeleton, snaplines"
                "misc" -> "Fly, Godmode, Rapid fire, Speed"
                else -> "Feature automation & gestures"
            }

            val isSelected = selectedScript?.name == s.name
            v.setBackgroundResource(if (isSelected) R.drawable.bg_card_selected else R.drawable.bg_card)

            return v
        }
    }
}

/** Holds MediaProjection result data until ScreenCaptureService reads it. */
object ScreenCaptureHolder {
    var resultCode: Int = Activity.RESULT_CANCELED
    var resultData: Intent? = null
}