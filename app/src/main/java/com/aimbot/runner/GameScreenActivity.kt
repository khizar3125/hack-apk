package com.aimbot.runner

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.aimbot.runner.databinding.ActivityGameScreenBinding
import org.json.JSONObject

class GameScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameScreenBinding
    private var gameName = ""
    private var scriptName = ""
    private var scriptType = ""
    private var config = JSONObject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameName = intent.getStringExtra("game_name") ?: ""
        scriptName = intent.getStringExtra("script_name") ?: ""
        scriptType = intent.getStringExtra("script_type") ?: ""
        val configStr = intent.getStringExtra("script_config") ?: "{}"
        config = JSONObject(configStr)

        binding.tvGameName.text = "Game: $gameName"
        binding.tvScriptName.text = "Script: $scriptName ($scriptType)"

        buildFeatureToggles()

        binding.btnApply.setOnClickListener {
            saveFeatures()
            finish()
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun buildFeatureToggles() {
        binding.container.removeAllViews()

        when (scriptType) {
            "aimbot" -> {
                addToggle("Aimbot enabled", "aimbot", config.optBoolean("aimbot", true))
                addToggle("Auto fire", "autoFire", config.optBoolean("autoFire", false))
                addToggle("Visibility check", "visibilityCheck", config.optBoolean("visibilityCheck", true))
                addSeekBar("FOV", "fov", config.optInt("fov", 90).toFloat(), 30f, 120f)
                addSeekBar("Smooth", "smooth", config.optDouble("smooth", 1.0).toFloat(), 0.5f, 3.0f)
                addSeekBar("Aim FOV", "aimFov", config.optInt("aimFov", 80).toFloat(), 30f, 120f)
                addSpinner("Bone", "bone", arrayOf("Head", "Neck", "Chest"), 0)
            }
            "esp" -> {
                addToggle("Box", "box", config.optBoolean("box", true))
                addToggle("Skeleton", "skeleton", config.optBoolean("skeleton", true))
                addToggle("Distance", "distance", config.optBoolean("distance", true))
                addToggle("Health", "health", config.optBoolean("health", true))
                addToggle("Name", "name", config.optBoolean("name", true))
                addToggle("Snapline", "snapline", config.optBoolean("snapline", true))
                if (config.has("imposterHighlight")) {
                    addToggle("Imposter highlight", "imposterHighlight", config.optBoolean("imposterHighlight", false))
                }
                if (config.has("xray")) {
                    addToggle("X-ray", "xray", config.optBoolean("xray", false))
                }
            }
            "misc" -> {
                if (config.has("fly")) addToggle("Fly", "fly", config.optBoolean("fly", false))
                if (config.has("speed")) addToggle("Speed", "speed", config.optBoolean("speed", false))
                if (config.has("godmode")) addToggle("God mode", "godmode", config.optBoolean("godmode", false))
                if (config.has("nocrecoil")) addToggle("No recoil", "nocrecoil", config.optBoolean("nocrecoil", true))
                if (config.has("killrange")) addToggle("Kill range", "killrange", config.optBoolean("killrange", false))
                if (config.has("coinmagnet")) addToggle("Coin magnet", "coinmagnet", config.optBoolean("coinmagnet", false))
                if (config.has("autoplay")) addToggle("Auto play", "autoplay", config.optBoolean("autoplay", false))
                if (config.has("mult")) {
                    addSeekBar("Multiplier", "mult", config.optDouble("mult", 2.0).toFloat(), 1.0f, 10.0f)
                }
            }
        }
    }

    private fun addToggle(label: String, key: String, checked: Boolean) {
        val cb = CheckBox(this)
        cb.text = label
        cb.isChecked = checked
        cb.setTextColor(0xFFFFFFFF)
        cb.tag = key
        binding.container.addView(cb)
    }

    private fun addSeekBar(label: String, key: String, value: Float, min: Float, max: Float) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val tv = TextView(this)
        tv.text = "$label: ${String.format("%.1f", value)}"
        tv.textColor = 0xFFFFFFFF
        tv.layoutParams = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT)
        val sb = SeekBar(this)
        sb.tag = key // tag stays as the config key String
        sb.max = ((max - min) * 10).toInt()
        sb.progress = ((value - min) * 10).toInt()
        sb.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sb.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val v = min + progress / 10f
                tv.text = "$label: ${String.format("%.1f", v)}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        row.addView(tv)
        row.addView(sb)
        binding.container.addView(row)
    }

    private fun addSpinner(label: String, key: String, items: Array<String>, selected: Int) {
        val tv = TextView(this)
        tv.text = label
        tv.textColor = 0xFFFFFFFF
        binding.container.addView(tv)
        val sp = Spinner(this)
        sp.tag = key
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.adapter = adapter
        sp.setSelection(selected)
        binding.container.addView(sp)
    }

    private fun saveFeatures() {
        val count = binding.container.childCount
        for (i in 0 until count) {
            val v = binding.container.getChildAt(i)
            when (v) {
                is CheckBox -> {
                    val key = v.tag as? String ?: v.text.toString()
                    config.put(key, v.isChecked)
                }
                is LinearLayout -> {
                    // Row contains TextView + SeekBar; read progress from seekbar directly
                    val seekBar = v.getChildAt(1)
                    if (seekBar is SeekBar) {
                        val key = seekBar.tag as? String
                        val min = 0.0f
                        val max = (seekBar.max / 10f) + min
                        val value = min + seekBar.progress / 10f
                        if (key != null) config.put(key, value)
                    }
                }
                is Spinner -> {
                    val key = v.tag as? String
                    val value = (v as Spinner).selectedItem.toString()
                    if (key != null) config.put(key, value)
                }
            }
        }
        // Persist modified config so ScriptExecutor can read it at run time.
        val prefs: SharedPreferences = getSharedPreferences("runner_config", Context.MODE_PRIVATE)
        prefs.edit().putString("script_config_${gameName}_${scriptName}", config.toString()).apply()
    }
}
