package com.aimbot.runner.script

import android.content.Context
import android.util.Log
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import com.aimbot.runner.root.MemoryEngine
import com.aimbot.runner.root.NonRootAccessibilityService
import com.aimbot.runner.root.NonRootInputInjector
import com.aimbot.runner.root.RootAccess
import org.json.JSONArray
import org.json.JSONObject

/**
 * Owns the memory engine. One game + one script at a time.
 */
class ScriptRunner(private val context: Context) {

    /** Shared engine so the executor and the runner read the same pid. */
    val engine = MemoryEngine()

    private var currentGame: GameConfig? = null
    private var currentScript: ScriptConfig? = null
    private var running = false
    /** When true, memory attach is skipped and scripts run via accessibility input only. */
    var nonRootMode: Boolean = false
        private set

    /** Reason the last start() failed, surfaced to the UI. */
    @Volatile
    var lastError: String = ""
        private set

    fun loadGames(): List<GameConfig> {
        val list = mutableListOf<GameConfig>()
        try {
            // Prefer saved offsets from internal storage (written by AutoConfigActivity)
            val savedFile = java.io.File(context.filesDir, "games.json")
            val json: String
            if (savedFile.exists()) {
                json = savedFile.bufferedReader().use { it.readText() }
            } else {
                json = context.assets.open("games.json").bufferedReader().use { it.readText() }
            }
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val offsets = if (o.has("offsets") && !o.isNull("offsets")) o.getJSONObject("offsets") else null
                val bypass = stringList(o, "bypass")
                val features = stringList(o, "features")
                list.add(
                    GameConfig(
                        name = o.getString("name"),
                        packageName = o.getString("package"),
                        module = o.getString("module"),
                        offsets = offsets,
                        anticheat = if (o.has("anticheat") && !o.isNull("anticheat")) o.getString("anticheat") else null,
                        category = if (o.has("category") && !o.isNull("category")) o.getString("category") else null,
                        bypass = bypass,
                        features = features,
                        research = if (o.has("research") && !o.isNull("research")) o.getString("research") else null
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ScriptRunner", "loadGames failed", e)
        }
        return list
    }

    fun loadScripts(): List<ScriptConfig> {
        val list = mutableListOf<ScriptConfig>()
        try {
            val json = context.assets.open("scripts.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val config = if (o.has("config") && !o.isNull("config")) o.getJSONObject("config") else null
                list.add(
                    ScriptConfig(
                        name = o.getString("name"),
                        type = o.getString("type"),
                        game = o.getString("game"),
                        config = config,
                        bypass = stringList(o, "bypass")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ScriptRunner", "loadScripts failed", e)
        }
        return list
    }

    private fun stringList(o: JSONObject, key: String): List<String> {
        if (!o.has(key) || o.isNull(key)) return emptyList()
        val arr = o.getJSONArray(key)
        val out = mutableListOf<String>()
        for (j in 0 until arr.length()) out.add(arr.getString(j))
        return out
    }

    fun scriptsForGame(gameName: String): List<ScriptConfig> {
        return loadScripts().filter { it.game == gameName }
    }

    /**
     * Attaches to the game and resolves its module.
     *
     * The target game process must already be running and the device must be
     * rooted: reading another app's memory is not possible from a normal
     * unprivileged app.
     */
    fun start(game: GameConfig, script: ScriptConfig, screenW: Int, screenH: Int): Boolean {
        if (running) stop()
        currentGame = game
        currentScript = script
        nonRootMode = false

        if (!RootAccess.probe()) {
            lastError = "Root access is required to read game memory. " +
                "Grant root to the app, then try again."
            Log.e("ScriptRunner", lastError)
            return false
        }

        var attached = engine.attach(game.packageName)
        if (!attached) {
            val startAttach = System.currentTimeMillis()
            while (!attached && (System.currentTimeMillis() - startAttach) < 12000L) {
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                attached = engine.attach(game.packageName)
            }
        }

        if (!attached) {
            lastError = "${game.name} is not running. Start the game first, " +
                "then run the script."
            Log.e("ScriptRunner", lastError)
            return false
        }

        var moduleResolved = engine.resolveModule(game.module)
        if (!moduleResolved) {
            val startModule = System.currentTimeMillis()
            while (!moduleResolved && (System.currentTimeMillis() - startModule) < 12000L) {
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                moduleResolved = engine.resolveModule(game.module)
            }
        }

        if (!moduleResolved) {
            lastError = "${game.module} is not loaded in ${game.name}. " +
                "The game has to reach its main scene before scripts attach."
            Log.e("ScriptRunner", lastError)
            engine.detach()
            return false
        }

        val offs = game.offsets ?: JSONObject()
        engine.offLocalPlayer = offs.optLong("localPlayer", 0x16F0A00)
        engine.offCamera = offs.optLong("camera", 0x16F0B80)
        engine.offEntityList = offs.optLong("entityList", 0x1720000)
        engine.offMaxEnt = offs.optInt("maxEnt", 64)
        engine.offTeam = offs.optInt("teamOff", 0x2E8)
        engine.offHealth = offs.optInt("healthOff", 0x2D0)
        engine.offPos = offs.optInt("posOff", 0x138)
        engine.camPosOff = offs.optInt("camPosOff", 0x100)
        engine.camForwardOff = offs.optInt("camForwardOff", 0x110)
        engine.camUpOff = offs.optInt("camUpOff", 0x120)
        engine.camRightOff = offs.optInt("camRightOff", 0x130)

        running = true
        Log.i("ScriptRunner", "engine live for ${game.name} pid=${engine.pid}")
        return true
    }

    /**
     * Non-root mode: skips memory attach entirely. Scripts run through
     * AccessibilityService gestures (tap/swipe) only. No memory read/write
     * is possible, so aimbot/ESP/wallhack are unavailable here — only
     * auto-tap, auto-fire, auto-play and overlay drawing work.
     */
    fun startNonRoot(game: GameConfig, script: ScriptConfig, screenW: Int, screenH: Int): Boolean {
        if (running) stop()
        currentGame = game
        currentScript = script
        nonRootMode = true

        NonRootInputInjector.init(context)
        val svc = NonRootAccessibilityService.instance
        if (svc != null) {
            NonRootInputInjector.bindService(svc, screenW, screenH)
        }

        running = true
        lastError = ""
        Log.i("ScriptRunner", "non-root mode live for ${game.name}")
        return true
    }

    private fun runnerApplyBypass(b: String) {
        when (b) {
            "signature_scramble" -> engine.applySignatureScramble()
            "handle_obfuscate" -> engine.applyHandleObfuscate()
            "sleep_jitter" -> engine.applySleepJitter()
            "anti_tamper_patch" -> engine.applyAntiTamperPatch()
            else -> Log.w("ScriptRunner", "unknown bypass '$b'")
        }
    }

    fun applyBypass(b: String) {
        runnerApplyBypass(b)
    }

    fun stop() {
        running = false
        engine.detach()
        currentGame = null
        currentScript = null
    }

    fun isRunning(): Boolean = running && (nonRootMode || engine.pid != 0)

    fun status(): String {
        if (!running) return "stopped"
        if (nonRootMode) return "running (non-root mode)"
        return "pid=${engine.pid} base=0x${engine.moduleBase.toString(16)}"
    }

    fun activeGame(): GameConfig? = currentGame
}
