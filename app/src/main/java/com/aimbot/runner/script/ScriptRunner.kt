package com.aimbot.runner.script

import android.content.Context
import android.content.Intent
import android.util.Log
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import org.json.JSONObject

class ScriptRunner(private val context: Context) {

    private var currentGame: GameConfig? = null
    private var currentScript: ScriptConfig? = null
    private var running = false
    private var aimThread: Thread? = null

    fun loadGames(): List<GameConfig> {
        val list = mutableListOf<GameConfig>()
        try {
            val stream: java.io.InputStream = context.assets.open("games.json")
            val json = stream.bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val offsets = if (o.has("offsets") && !o.isNull("offsets")) o.getJSONObject("offsets") else null
                list.add(GameConfig(
                    name = o.getString("name"),
                    packageName = o.getString("package"),
                    module = o.getString("module"),
                    offsets = offsets
                ))
            }
        } catch (e: Exception) {
            Log.e("ScriptRunner", "loadGames failed", e)
        }
        return list
    }

    fun loadScripts(): List<ScriptConfig> {
        val list = mutableListOf<ScriptConfig>()
        try {
            val stream: java.io.InputStream = context.assets.open("scripts.json")
            val json = stream.bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val config = if (o.has("config") && !o.isNull("config")) o.getJSONObject("config") else null
                list.add(ScriptConfig(
                    name = o.getString("name"),
                    type = o.getString("type"),
                    game = o.getString("game"),
                    config = config
                ))
            }
        } catch (e: Exception) {
            Log.e("ScriptRunner", "loadScripts failed", e)
        }
        return list
    }

    fun scriptsForGame(gameName: String): List<ScriptConfig> {
        return loadScripts().filter { it.game == gameName }
    }

    fun start(game: GameConfig, script: ScriptConfig, screenW: Int, screenH: Int): Boolean {
        if (running) stop()
        currentGame = game
        currentScript = script
        running = NativeBridge.nativeStart(game.packageName, game.module, screenW, screenH)
        if (!running) return false

        // Apply bypasses from script config
        val bypasses = script.config?.getJSONArray("bypass")
        if (bypasses != null) {
            for (i in 0 until bypasses.length()) {
                val b = bypasses.getString(i)
                val type = when (b) {
                    "signature_scramble" -> 0
                    "handle_obfuscate" -> 1
                    "sleep_jitter" -> 2
                    "anti_tamper_patch" -> 3
                    else -> -1
                }
                if (type >= 0) NativeBridge.nativeBypass(type)
            }
        }

        aimThread = Thread { aimLoop(screenW, screenH, script) }
        aimThread?.isDaemon = true
        aimThread?.start()
        return true
    }

    fun applyBypass(b: String) {
        val type = when (b) {
            "signature_scramble" -> 0
            "handle_obfuscate" -> 1
            "sleep_jitter" -> 2
            "anti_tamper_patch" -> 3
            else -> -1
        }
        if (type >= 0) NativeBridge.nativeBypass(type)
    }

    fun stop() {
        running = false
        NativeBridge.nativeStop()
        aimThread?.interrupt()
        aimThread = null
        currentGame = null
        currentScript = null
    }

    fun isRunning(): Boolean = running && NativeBridge.nativeIsRunning()

    private fun aimLoop(screenW: Int, screenH: Int, script: ScriptConfig) {
        val cfg = script.config ?: JSONObject()
        val fov = cfg.optFloat("fov", 90f)
        val smooth = cfg.optFloat("smooth", 1.0f)
        val aimFov = cfg.optFloat("aimFov", 80f)

        while (running && !Thread.currentThread().isInterrupted) {
            val result = NativeBridge.nativeAimTick(screenW, screenH, fov, smooth, aimFov)
            if (result.size >= 2 && result[0] != 0f && result[1] != 0f) {
                // target locked: result[0]=x, result[1]=y
                // smoothing applied by caller overlay if desired
            }
            NativeBridge.nativeBypass(2) // sleep_jitter every tick
            Thread.sleep(16) // ~60 FPS
        }
    }

    fun status(): String = NativeBridge.nativeStatus()
}
