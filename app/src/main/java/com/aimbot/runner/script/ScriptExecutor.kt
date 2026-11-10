package com.aimbot.runner.script

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aimbot.runner.OverlayService
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import com.aimbot.runner.root.InputWriter
import com.aimbot.runner.root.MemoryEngine
import com.aimbot.runner.root.RootAccess
import org.json.JSONObject

/**
 * Reads a feature flag that may be stored as a boolean or as a number.
 * scripts.json uses both: "godmode": true but "speed": 2.0.
 */
private fun JSONObject.flagOn(key: String): Boolean {
    if (!has(key) || isNull(key)) return false
    return when (val v = get(key)) {
        is Boolean -> v
        is Number -> v.toDouble() > 0.0
        is String -> v.equals("true", ignoreCase = true)
        else -> false
    }
}

/**
 * Runs one script at a time. The native engine is single-instance, so every
 * execute() clears the previous state first.
 */
class ScriptExecutor(private val context: Context, private val runner: ScriptRunner) {

    private val active = mutableMapOf<String, Boolean>()

    /** Reason the last execute() failed, for display in the app. */
    @Volatile
    var lastError: String = ""
        private set

    /** Same engine the runner attached with, so the pid stays in sync. */
    private val engine: MemoryEngine get() = runner.engine

    private fun loadPersistedConfig(gameName: String, scriptName: String, fallback: JSONObject?): JSONObject {
        val prefs: SharedPreferences = context.getSharedPreferences("runner_config", Context.MODE_PRIVATE)
        val saved = prefs.getString("script_config_${gameName}_${scriptName}", null)
        return if (saved != null) JSONObject(saved) else (fallback ?: JSONObject())
    }

    fun execute(game: GameConfig, script: ScriptConfig, screenW: Int, screenH: Int): Boolean {
        val key = "${game.name}|${script.name}"
        if (active[key] == true) return false

        // Single engine: clear whatever was running.
        active.clear()
        runner.stop()

        val cfg = loadPersistedConfig(game.name, script.name, script.config)
        val updatedScript = script.copy(config = cfg)

        if (!runner.start(game, updatedScript, screenW, screenH)) {
            active[key] = false
            lastError = runner.lastError
            Log.e("ScriptExecutor", "start failed: $lastError")
            return false
        }

        lastError = ""

        for (b in game.bypasses()) runner.applyBypass(b)

        active[key] = true

        dispatch(key, game, updatedScript, cfg, screenW, screenH)

        Log.i("ScriptExecutor", "started ${updatedScript.type} for ${game.name}")
        return true
    }

    private fun dispatch(
        key: String,
        game: GameConfig,
        script: ScriptConfig,
        cfg: JSONObject,
        screenW: Int,
        screenH: Int
    ) {
        when (script.type.lowercase()) {
            "aimbot" -> startAimbot(key, game, cfg, screenW, screenH)
            "esp" -> startEsp(key, game, cfg, screenW, screenH)
            "misc" -> startMisc(key, game, cfg, screenW, screenH)
            else -> Log.w("ScriptExecutor", "unknown script type: ${script.type}")
        }
    }

    /**
     * "misc" scripts are bundles of independent features. Each flag in the
     * config starts its own loop, so a single "Godmode" script with
     * {"godmode":true,"fly":true,"speed":2.0} runs all three.
     */
    private fun startMisc(key: String, game: GameConfig, cfg: JSONObject, screenW: Int, screenH: Int) {
        var started = 0

        if (cfg.flagOn("godmode")) {
            startGodmode(key, game, cfg); started++
        }
        if (cfg.flagOn("fly")) {
            startFly(key, game, cfg); started++
        }
        if (cfg.flagOn("speed") || cfg.flagOn("multiplier2x")) {
            startSpeed(key, game, cfg); started++
        }
        if (cfg.flagOn("nocrecoil") || cfg.optDouble("recoilScale", 1.0) <= 0.0) {
            startNoRecoil(key, game, cfg); started++
        }
        if (cfg.flagOn("oneHit") || cfg.flagOn("onehit")) {
            startOneHit(key, game, cfg); started++
        }
        if (cfg.flagOn("coinMagnet")) {
            startCoinMagnet(key, game, cfg); started++
        }
        if (cfg.flagOn("xray") || cfg.flagOn("fullbright")) {
            startXray(key, game, cfg); started++
        }
        if (cfg.flagOn("autoPlay")) {
            startAutoPlay(key, cfg, screenW, screenH); started++
        }
        if (cfg.flagOn("alwaysImpostor")) {
            startImpostorHighlight(key, game, cfg); started++
        }
        if (cfg.optDouble("killRange", 0.0) > 0.0) {
            startKillRange(key, game, cfg); started++
        }
        if (cfg.flagOn("rapidFire")) {
            startRapidFire(key, game, cfg); started++
        }

        if (started == 0) {
            Log.w("ScriptExecutor", "misc script matched no known feature flag")
            startGeneric(key)
        }
    }

    fun stop(script: ScriptConfig, game: GameConfig) {
        val key = "${game.name}|${script.name}"
        active[key] = false
        runner.stop()
        engine.detach()
        OverlayService.instance?.setTargets(emptyList())
        OverlayService.instance?.setTarget(0f, 0f, false)
        Log.i("ScriptExecutor", "stopped ${script.type} for ${game.name}")
    }

    fun isActive(script: ScriptConfig, game: GameConfig): Boolean {
        return active["${game.name}|${script.name}"] == true
    }

    /* ---------- per-feature execution loops ---------- */

    /**
     * ESP loop: reads the entity list every tick and pushes the projected
     * positions to the overlay.
     */
    private fun startEsp(key: String, game: GameConfig, cfg: JSONObject, screenW: Int, screenH: Int) {
        applyOffsets(game, cfg)
        val thread = Thread {
            val fov = cfg.optDouble("fov", 90.0).toFloat()
            val aimFov = cfg.optDouble("aimFov", 80.0).toFloat()
            engine.fov = fov
            engine.aimFov = aimFov

            while (active[key] == true && !Thread.interrupted()) {
                try {
                    val targets = engine.collectTargets(screenW, screenH)
                    OverlayService.instance?.setTargets(targets)
                    Thread.sleep(30)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e("ScriptExecutor", "esp loop error", e)
                    break
                }
            }
            OverlayService.instance?.setTargets(emptyList())
        }
        thread.isDaemon = true
        thread.start()
    }

    /**
     * Aimbot loop: picks the closest enemy inside the FOV and drags the
     * touch pointer toward it. Smoothing controls how much of the gap is
     * closed per tick.
     */
    private fun startAimbot(key: String, game: GameConfig, cfg: JSONObject, screenW: Int, screenH: Int) {
        applyOffsets(game, cfg)
        val thread = Thread {
            val fov = cfg.optDouble("fov", 90.0).toFloat()
            val aimFov = cfg.optDouble("aimFov", 80.0).toFloat()
            val smooth = cfg.optDouble("smooth", 1.0).toFloat()
            engine.fov = fov
            engine.aimFov = aimFov

            // The touch pointer has to be parked before a drag starts.
            InputWriter.probe()

            var currentX = screenW / 2f
            var currentY = screenH / 2f

            while (active[key] == true && !Thread.interrupted()) {
                try {
                    val targets = engine.collectTargets(screenW, screenH)
                    OverlayService.instance?.setTargets(targets)

                    val target = targets.firstOrNull()
                    if (target != null) {
                        // smooth is a fraction of the gap closed per tick.
                        val step = smooth.coerceIn(0.1f, 1f)
                        val nextX = currentX + (target.screenX - currentX) * step
                        val nextY = currentY + (target.screenY - currentY) * step
                        InputWriter.drag(screenW, screenH, currentX, currentY, nextX, nextY, 4)
                        currentX = nextX
                        currentY = nextY
                        OverlayService.instance?.setTarget(target.screenX, target.screenY, true)
                    } else {
                        currentX = screenW / 2f
                        currentY = screenH / 2f
                        OverlayService.instance?.setTarget(0f, 0f, false)
                    }
                    Thread.sleep(16)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e("ScriptExecutor", "aimbot loop error", e)
                    break
                }
            }
            OverlayService.instance?.setTarget(0f, 0f, false)
        }
        thread.isDaemon = true
        thread.start()
    }

    /** Copies the per-game offsets from games.json into the engine. */
    private fun applyOffsets(game: GameConfig, cfg: JSONObject) {
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
    }

    /** Address for a per-game feature field, or 0 when not configured. */
    private fun offs(game: GameConfig, key: String): Long =
        game.offsets?.optLong(key, 0L) ?: 0L

    /**
     * Runs a daemon loop until the script is stopped. Every feature writes to
     * a per-game address from games.json; when the address is 0 (not dumped
     * for that game version) the loop is a safe no-op instead of corrupting
     * memory.
     */
    private fun loop(key: String, delayMs: Long, block: () -> Unit) {
        val thread = Thread {
            while (active[key] == true && !Thread.interrupted()) {
                try {
                    block()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e("ScriptExecutor", "misc loop error", e)
                    break
                }
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startGodmode(key: String, game: GameConfig, cfg: JSONObject) {
        val addr = offs(game, "healthAddr")
        val value = cfg.optInt("healthValue", 99999)
        loop(key, 80) {
            if (addr != 0L) engine.writeInt(engine.moduleBase + addr, value)
        }
    }

    private fun startFly(key: String, game: GameConfig, cfg: JSONObject) {
        val addr = offs(game, "flyAddr")
        loop(key, 60) {
            if (addr != 0L) engine.writeInt(engine.moduleBase + addr, 1)
        }
    }

    private fun startSpeed(key: String, game: GameConfig, cfg: JSONObject) {
        val addr = offs(game, "speedAddr")
        val mult = cfg.optDouble("speed", 2.0).toFloat()
        loop(key, 60) {
            if (addr != 0L) engine.writeFloat(engine.moduleBase + addr, mult)
        }
    }

    private fun startNoRecoil(key: String, game: GameConfig, cfg: JSONObject) {
        val addr = offs(game, "recoilAddr")
        val scale = cfg.optDouble("recoilScale", 0.0).toFloat()
        loop(key, 60) {
            if (addr != 0L) engine.writeFloat(engine.moduleBase + addr, scale)
        }
    }

    private fun startOneHit(key: String, game: GameConfig, cfg: JSONObject) {
        val addr = offs(game, "damageAddr")
        val value = cfg.optInt("damageValue", 99999)
        loop(key, 60) {
            if (addr != 0L) engine.writeInt(engine.moduleBase + addr, value)
        }
    }

    private fun startCoinMagnet(key: String, game: GameConfig, cfg: JSONObject) {
        val addr = offs(game, "magnetAddr")
        val radius = cfg.optDouble("coinMagnetRadius", 1200.0).toFloat()
        loop(key, 80) {
            if (addr != 0L) engine.writeFloat(engine.moduleBase + addr, radius)
        }
    }

    private fun startXray(key: String, game: GameConfig, cfg: JSONObject) {
        val render = offs(game, "renderAddr")
        val bright = offs(game, "brightAddr")
        loop(key, 80) {
            if (render != 0L) engine.writeInt(engine.moduleBase + render, 1)
            if (bright != 0L) engine.writeInt(engine.moduleBase + bright, 15)
        }
    }

    /** Tap-based automation: repeatedly taps configured screen points. */
    private fun startAutoPlay(key: String, cfg: JSONObject, screenW: Int, screenH: Int) {
        val taps = cfg.optJSONArray("tapPoints")
        loop(key, 400) {
            if (taps != null) {
                for (i in 0 until taps.length()) {
                    val p = taps.getJSONObject(i)
                    InputWriter.tap(
                        screenW, screenH,
                        p.optDouble("x", 0.0).toFloat(),
                        p.optDouble("y", 0.0).toFloat()
                    )
                    Thread.sleep(120)
                }
            }
        }
    }

    private fun startImpostorHighlight(key: String, game: GameConfig, cfg: JSONObject) {
        val addr = offs(game, "roleAddr")
        val value = cfg.optInt("roleValue", 1)
        loop(key, 100) {
            if (addr != 0L) engine.writeInt(engine.moduleBase + addr, value)
        }
    }

    private fun startKillRange(key: String, game: GameConfig, cfg: JSONObject) {
        val addr = offs(game, "killRangeAddr")
        val range = cfg.optDouble("killRange", 5.0).toFloat()
        loop(key, 100) {
            if (addr != 0L) engine.writeFloat(engine.moduleBase + addr, range)
        }
    }

    private fun startRapidFire(key: String, game: GameConfig, cfg: JSONObject) {
        val addr = offs(game, "fireRateAddr")
        val rate = cfg.optDouble("rapidFireRate", 0.05).toFloat()
        loop(key, 60) {
            if (addr != 0L) engine.writeFloat(engine.moduleBase + addr, rate)
        }
    }

    /** Fallback for misc scripts with no recognised flag: keep the engine alive. */
    private fun startGeneric(key: String) {
        loop(key, 200) { }
    }
}
