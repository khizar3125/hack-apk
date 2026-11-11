package com.aimbot.runner

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aimbot.runner.databinding.ActivityGameScreenBinding
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

class GameScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameScreenBinding
    private var gameName = ""
    private var scriptName = ""
    private var scriptType = ""
    private var config = JSONObject()
    private var nonRootMode: Boolean = false

    // Seekbar bounds are tracked per key so saveFeatures() can reverse the
    // progress back to a real value instead of assuming min=0.
    private val seekBounds = mutableMapOf<String, Pair<Float, Float>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameName = intent.getStringExtra("game_name") ?: ""
        scriptName = intent.getStringExtra("script_name") ?: ""
        scriptType = intent.getStringExtra("script_type") ?: ""
        val configStr = intent.getStringExtra("script_config") ?: "{}"
        config = try {
            JSONObject(configStr)
        } catch (_: Exception) {
            JSONObject()
        }
        nonRootMode = intent.getBooleanExtra("non_root_mode", false)

        binding.tvGameName.text = "Game: $gameName"
        binding.tvScriptName.text = "Script: $scriptName ($scriptType)"

        if (nonRootMode) {
            val tv = TextView(this).apply {
                text = "Non-root mode: memory features disabled. Use auto-tap / auto-fire / auto-play."
                setTextColor(0xFFFFFF00.toInt())
                textSize = 14f
            }
            binding.container.addView(tv)
        }

        buildFeatureToggles()

        binding.btnApply.setOnClickListener {
            saveFeatures()
            Toast.makeText(this, "Applied", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun buildFeatureToggles() {
        binding.container.removeAllViews()
        seekBounds.clear()

        when (scriptType.lowercase()) {
            "aimbot" -> {
                if (!nonRootMode) {
                    addToggle("Aimbot enabled", "aimbot", config.optBoolean("aimbot", config.optBoolean("enabled", true)))
                    addToggle("Auto fire", "autoFire", config.optBoolean("autoFire", false))
                    addToggle("Team check", "teamCheck", config.optBoolean("teamCheck", false))
                    addSeekBar("FOV", "fov", config.optDouble("fov", 90.0).toFloat(), 30f, 120f)
                    addSeekBar("Smooth", "smooth", config.optDouble("smooth", 1.0).toFloat(), 0.5f, 3.0f)
                    addSeekBar("Aim FOV", "aimFov", config.optDouble("aimFov", 80.0).toFloat(), 30f, 120f)
                    addSpinner("Bone", "bone", arrayOf("head", "neck", "chest"), 0)
                } else {
                    val tv = TextView(this).apply {
                        text = "Aimbot needs root (memory read). Non-root: use Auto tap / Auto fire instead."
                        setTextColor(0xFFFFFF00.toInt())
                        textSize = 14f
                    }
                    binding.container.addView(tv)
                }
            }
            "esp" -> {
                if (!nonRootMode) {
                    addToggle("Box", "box", config.optBoolean("box", true))
                    addToggle("Skeleton", "skeleton", config.optBoolean("skeleton", true))
                    addToggle("Distance", "distance", config.optBoolean("distance", true))
                    addToggle("Health", "health", config.optBoolean("health", true))
                    addToggle("Name", "name", config.optBoolean("name", true))
                    addToggle("Snapline", "snapline", config.optBoolean("snapline", true))
                } else {
                    val tv = TextView(this).apply {
                        text = "ESP needs root (memory read). Non-root: enable Screen Capture for color-based overlay."
                        setTextColor(0xFFFFFF00.toInt())
                        textSize = 14f
                    }
                    binding.container.addView(tv)
                }
            }
            "misc" -> {
                // Mirror the actual config keys used by ScriptExecutor so the
                // toggles and the runtime agree.
                if (!nonRootMode) {
                    addToggle("God mode", "godmode", config.flagOn("godmode"))
                    addToggle("Fly", "fly", config.flagOn("fly"))
                    addToggle("No recoil", "nocrecoil", config.optDouble("recoilScale", 1.0) <= 0.0)
                    addToggle("One hit", "oneHit", config.flagOn("oneHit"))
                    addToggle("Coin magnet", "coinMagnet", config.flagOn("coinMagnet"))
                    addToggle("X-ray", "xray", config.flagOn("xray"))
                    addToggle("Always impostor", "alwaysImpostor", config.flagOn("alwaysImpostor"))
                    addSeekBar("Kill range", "killRange", config.optDouble("killRange", 0.0).toFloat(), 0f, 20f)
                    addSeekBar("Speed value", "speedValue", config.optDouble("speed", 2.0).toFloat(), 1f, 10f)
                }
                // These work in both root and non-root modes.
                addToggle("Speed (non-root)", "speedEnabled", config.flagOn("speed"))
                addToggle("Auto play", "autoPlay", config.flagOn("autoPlay"))
                if (nonRootMode) {
                    addToggle("Auto tap", "autoTap", config.flagOn("autoTap"))
                    addToggle("Auto fire", "autoFire", config.flagOn("autoFire"))
                    addSeekBar("Fire interval (ms)", "fireInterval", config.optLong("fireInterval", 200).toFloat(), 50f, 2000f)
                }
            }
            else -> {
                val tv = TextView(this).apply {
                    text = "No options for this script type."
                    setTextColor(0xFFFFFFFF.toInt())
                }
                binding.container.addView(tv)
            }
        }
    }

    private fun addToggle(label: String, key: String, checked: Boolean) {
        val cb = CheckBox(this).apply {
            text = label
            isChecked = checked
            setTextColor(0xFFFFFFFF.toInt())
            tag = key
        }
        binding.container.addView(cb)
    }

    private fun addSeekBar(label: String, key: String, value: Float, min: Float, max: Float) {
        seekBounds[key] = min to max
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val tv = TextView(this).apply {
            text = "$label: ${fmt(value)}"
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val steps = ((max - min) * 10).toInt().coerceAtLeast(1)
        val sb = SeekBar(this).apply {
            tag = key
            this.max = steps
            progress = (((value - min) * 10).toInt()).coerceIn(0, steps)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    tv.text = "$label: ${fmt(min + progress / 10f)}"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        row.addView(tv)
        row.addView(sb)
        binding.container.addView(row)
    }

    private fun addSpinner(label: String, key: String, items: Array<String>, selected: Int) {
        val tv = TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
        }
        binding.container.addView(tv)
        val sp = Spinner(this).apply {
            tag = key
            adapter = ArrayAdapter(this@GameScreenActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(selected.coerceIn(0, items.size - 1))
        }
        binding.container.addView(sp)
    }

    private fun saveFeatures() {
        val count = binding.container.childCount
        for (i in 0 until count) {
            when (val v = binding.container.getChildAt(i)) {
                is CheckBox -> {
                    val key = v.tag as? String ?: continue
                    // "speedEnabled" is the UI toggle for the numeric "speed"
                    // knob; the runtime reads "speed" as a number.
                    if (key == "speedEnabled") {
                        config.put("speedEnabled", v.isChecked)
                        if (!v.isChecked) config.put("speed", 0.0)
                        continue
                    }
                    config.put(key, v.isChecked)
                }
                is LinearLayout -> {
                    val seekBar = v.getChildAt(1) as? SeekBar ?: continue
                    val key = seekBar.tag as? String ?: continue
                    val (min, max) = seekBounds[key] ?: continue
                    val value = min + seekBar.progress / 10f
                    if (key == "speedValue") {
                        config.put("speed", value.toDouble())
                        config.put("multiplier", value.toDouble())
                    } else {
                        config.put(key, value.toDouble())
                    }
                }
                is Spinner -> {
                    val key = v.tag as? String ?: continue
                    config.put(key, v.selectedItem?.toString() ?: "")
                }
            }
        }

        val prefs: SharedPreferences = getSharedPreferences("runner_config", Context.MODE_PRIVATE)
        prefs.edit().putString("script_config_${gameName}_${scriptName}", config.toString()).apply()
    }

    private fun fmt(v: Float): String = String.format("%.1f", v)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
