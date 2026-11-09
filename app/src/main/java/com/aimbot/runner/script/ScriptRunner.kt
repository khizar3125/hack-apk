package com.aimbot.runner.script

import android.content.Context
import android.content.Intent
import android.util.Log
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import org.json.JSONArray
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
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val offsets = if (o.has("offsets") && !o.isNull("offsets")) o.getJSONObject("offsets") else null
                val bypassArr = if (o.has("bypass") && !o.isNull("bypass")) o.getJSONArray("bypass") else null
                val bypass = mutableListOf<String>()
                if (bypassArr != null) {
                    for (j in 0 until bypassArr.length()) bypass.add(bypassArr.getString(j))
                }
                val featuresArr = if (o.has("features") && !o.isNull("features")) o.getJSONArray("features") else null
                val features = mutableListOf<String>()
                if (featuresArr != null) {
                    for (j in 0 until featuresArr.length()) features.add(featuresArr.getString(j))
                }
                list.add(GameConfig(
                    name = o.getString("name"),
                    packageName = o.getString("package"),
                    module = o.getString("module"),
                    offsets = offsets,
                    anticheat = if (o.has("anticheat") && !o.isNull("anticheat")) o.getString("anticheat") else null,
                    bypass = bypass,
                    features = features,
                    research = if (o.has("research") && !o.isNull("research")) o.getString("research") else null
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
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val config = if (o.has("config") && !o.isNull("config")) o.getJSONObject("config") else null
                val bypassArr = if (o.has("bypass") && !o.isNull("bypass")) o.getJSONArray("bypass") else null
                val bypass = mutableListOf<String>()
                if (bypassArr != null) {
                    for (j in 0 until bypassArr.length()) bypass.add(bypassArr.getString(j))
                }
                list.add(ScriptConfig(
                    name = o.getString("name"),
                    type = o.getString("type"),
                    game = o.getString("game"),
                    config = config,
                    bypass = bypass
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
        NativeBridge.nativeInit()

        // Pass per-game offsets from JSON to native layer before start
        val offs = game.offsets ?: JSONObject()
        NativeBridge.nativeSetOffsets(
            offs.optLong("localPlayer", 0x16F0A00),
            offs.optLong("camera", 0x16F0B80),
            offs.optLong("entityList", 0x1720000),
            offs.optInt("maxEnt", 64),
            offs.optDouble("fov", 90.0).toFloat(),
            offs.optDouble("smooth", 1.0).toFloat(),
            offs.optDouble("aimFov", 80.0).toFloat(),
            offs.optLong("teamOff", 0x2E8),
            offs.optLong("healthOff", 0x2D0),
            offs.optLong("posOff", 0x138)
        )

        running = NativeBridge.nativeStart(game.packageName, game.module, screenW, screenH)
        if (!running) return false

        for (b in script.bypasses()) runnerApplyBypass(b)

        aimThread = Thread { aimLoop(screenW, screenH, script) }
        aimThread?.let { it.isDaemon = true }
        aimThread?.start()
        return true
    }

    private fun runnerApplyBypass(b: String) {
        val type = when (b) {
            "signature_scramble" -> 0
            "handle_obfuscate" -> 1
            "sleep_jitter" -> 2
            "anti_tamper_patch" -> 3
            else -> -1
        }
        if (type >= 0) NativeBridge.nativeBypass(type)
    }

    fun applyBypass(b: String) {
        runnerApplyBypass(b)
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
        val fov = cfg.optDouble("fov", 90.0).toFloat()
        val smooth = cfg.optDouble("smooth", 1.0).toFloat()
        val aimFov = cfg.optDouble("aimFov", 80.0).toFloat()

        while (running && !Thread.currentThread().isInterrupted) {
            try {
                val result = NativeBridge.nativeAimTick(screenW, screenH, fov, smooth, aimFov)
                if (result.size >= 2 && result[0] != 0f && result[1] != 0f) {
                    // target locked: result[0]=x, result[1]=y; overlay notified via OverlayService
                    com.aimbot.runner.OverlayService.instance?.setTarget(result[0], result[1], true)
                } else {
                    com.aimbot.runner.OverlayService.instance?.setTarget(0f, 0f, false)
                }
                NativeBridge.nativeBypass(2) // sleep_jitter every tick
                Thread.sleep(16) // ~60 FPS
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e("ScriptRunner", "aimLoop error", e)
                break
            }
        }
    }

    fun status(): String = NativeBridge.nativeStatus()
}
