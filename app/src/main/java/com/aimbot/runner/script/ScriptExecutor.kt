package com.aimbot.runner.script

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aimbot.runner.OverlayService
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import org.json.JSONObject

class ScriptExecutor(private val context: Context, private val runner: ScriptRunner) {

    private val active = mutableMapOf<String, Boolean>()

    private fun loadPersistedConfig(gameName: String, scriptName: String, fallback: JSONObject?): JSONObject {
        val prefs: SharedPreferences = context.getSharedPreferences("runner_config", Context.MODE_PRIVATE)
        val saved = prefs.getString("script_config_${gameName}_${scriptName}", null)
        return if (saved != null) JSONObject(saved) else (fallback ?: JSONObject())
    }

    fun execute(game: GameConfig, script: ScriptConfig, screenW: Int, screenH: Int): Boolean {
        val key = "${game.name}|${script.name}"
        if (active[key] == true) return false

        // Clear any previously active script (single native engine, one at a time)
        active.clear()

        // Load persisted config (from GameScreenActivity changes) and merge into script
        val cfg = loadPersistedConfig(game.name, script.name, script.config)
        val updatedScript = script.copy(config = cfg)

        // Initialize native engine first (sets targetPid, moduleBase, running flag, applies script bypasses)
        if (!runner.start(game, updatedScript, screenW, screenH)) {
            active[key] = false
            Log.e("ScriptExecutor", "nativeStart failed for ${game.name}")
            return false
        }

        // Apply game-specific bypasses after engine is live
        for (b in game.bypasses()) runner.applyBypass(b)

        active[key] = true

        val type = updatedScript.type.lowercase()

        when (type) {
            "esp" -> startEsp(key, game, cfg, screenW, screenH)
            "godmode" -> startGodmode(key, cfg)
            "fly" -> startFly(key, cfg)
            "speed" -> startSpeed(key, cfg)
            "norecoil" -> startNoRecoil(key, cfg)
            "one_hit" -> startOneHit(key, cfg)
            "unlimited_money" -> startUnlimitedMoney(key, cfg)
            "all_guns" -> startAllGuns(key, cfg)
            "vehicle_speed" -> startVehicleSpeed(key, cfg)
            "no_ads" -> startNoAds(key, cfg)
            "xray" -> startXray(key, cfg)
            "coin_magnet" -> startCoinMagnet(key, cfg)
            "autoplay" -> startAutoPlay(key, cfg)
            "impostor_highlight" -> startImpostorHighlight(key, cfg)
            "kill_range" -> startKillRange(key, cfg)
            "aimbot" -> {
                // aimbot loop already running inside runner.start(); no extra thread needed
                Log.i("ScriptExecutor", "aimbot loop active via runner.start for ${game.name}")
            }
            else -> Log.w("ScriptExecutor", "unknown script type: $type")
        }

        Log.i("ScriptExecutor", "started $type for ${game.name}")
        return true
    }

    fun stop(script: ScriptConfig, game: GameConfig) {
        val key = "${game.name}|${script.name}"
        active[key] = false
        runner.stop()
        Log.i("ScriptExecutor", "stopped ${script.type} for ${game.name}")
    }

    fun isActive(script: ScriptConfig, game: GameConfig): Boolean {
        return active["${game.name}|${script.name}"] == true
    }

    /* ---------- per-feature execution loops ---------- */

    private fun startAimbot(key: String, game: GameConfig, cfg: JSONObject, screenW: Int, screenH: Int) {
        val thread = Thread {
            val fov = cfg.optFloat("fov", 90f)
            val smooth = cfg.optFloat("smooth", 1.0f)
            val aimFov = cfg.optFloat("aimFov", 80f)
            val autoFire = cfg.optBoolean("autoFire", true)
            val bone = cfg.optString("bone", "head")

            while (active[key] == true && !Thread.interrupted()) {
                try {
                    val result = NativeBridge.nativeAimTick(screenW, screenH, fov, smooth, aimFov)
                    if (result.size >= 2 && result[0] != 0f && result[1] != 0f) {
                        // target locked at result[0], result[1]; overlay draws snapline
                        if (autoFire) NativeBridge.nativeBypass(2) // sleep_jitter before fire
                    }
                    NativeBridge.nativeBypass(2) // sleep_jitter every tick
                    Thread.sleep(16)
                } catch (e: InterruptedException) { break } catch (e: Exception) { Log.e("ScriptExecutor", "aimbot loop error", e); break }
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startEsp(key: String, game: GameConfig, cfg: JSONObject, screenW: Int, screenH: Int) {
        val thread = Thread {
            val box = cfg.optBoolean("box", true)
            val skeleton = cfg.optBoolean("skeleton", true)
            val distance = cfg.optBoolean("distance", true)
            val name = cfg.optBoolean("name", true)
            val health = cfg.optBoolean("health", true)
            val snapline = cfg.optBoolean("snapline", true)
            val fov = cfg.optFloat("fov", 90f)
            val smooth = cfg.optFloat("smooth", 1.0f)
            val aimFov = cfg.optFloat("aimFov", 80f)

            while (active[key] == true && !Thread.interrupted()) {
                try {
                    val result = NativeBridge.nativeAimTick(screenW, screenH, fov, smooth, aimFov)
                    if (result.size >= 2) {
                        val x = result[0]
                        val y = result[1]
                        val locked = x != 0f && y != 0f
                        OverlayService.instance?.setTarget(x, y, locked)
                    }
                    NativeBridge.nativeBypass(2) // sleep_jitter to avoid detection
                    Thread.sleep(16)
                } catch (e: InterruptedException) { break } catch (e: Exception) { Log.e("ScriptExecutor", "esp loop error", e); break }
            }
            // Clear target when ESP stops
            OverlayService.instance?.setTarget(0f, 0f, false)
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startGodmode(key: String, cfg: JSONObject) {
        val thread = Thread {
            val enabled = cfg.optBoolean("godmode", true)
            while (active[key] == true && !Thread.interrupted()) {
                // native godmode: freeze health via remote memory write
                NativeBridge.nativeBypass(0) // signature_scramble to hide health edits
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startFly(key: String, cfg: JSONObject) {
        val thread = Thread {
            val speed = cfg.optFloat("flySpeed", 5.0f)
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(1) // handle_obfuscate for position edits
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startSpeed(key: String, cfg: JSONObject) {
        val thread = Thread {
            val multiplier = cfg.optFloat("multiplier", 2.0f)
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(1)
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startNoRecoil(key: String, cfg: JSONObject) {
        val thread = Thread {
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(0) // signature_scramble to hide recoil patch
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startOneHit(key: String, cfg: JSONObject) {
        val thread = Thread {
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(0)
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startUnlimitedMoney(key: String, cfg: JSONObject) {
        val thread = Thread {
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(3) // anti_tamper_patch for economy
                Thread.sleep(500)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startAllGuns(key: String, cfg: JSONObject) {
        val thread = Thread {
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(3)
                Thread.sleep(500)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startVehicleSpeed(key: String, cfg: JSONObject) {
        val thread = Thread {
            val multiplier = cfg.optFloat("multiplier", 2.0f)
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(1)
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startNoAds(key: String, cfg: JSONObject) {
        val thread = Thread {
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(3)
                Thread.sleep(1000)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startXray(key: String, cfg: JSONObject) {
        val thread = Thread {
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(2)
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startCoinMagnet(key: String, cfg: JSONObject) {
        val thread = Thread {
            val radius = cfg.optFloat("radius", 10.0f)
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(2)
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startAutoPlay(key: String, cfg: JSONObject) {
        val thread = Thread {
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(2)
                Thread.sleep(200)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startImpostorHighlight(key: String, cfg: JSONObject) {
        val thread = Thread {
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(2)
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun startKillRange(key: String, cfg: JSONObject) {
        val thread = Thread {
            val range = cfg.optFloat("range", 5.0f)
            while (active[key] == true && !Thread.interrupted()) {
                NativeBridge.nativeBypass(0)
                Thread.sleep(100)
            }
        }
        thread.isDaemon = true
        thread.start()
    }
}
