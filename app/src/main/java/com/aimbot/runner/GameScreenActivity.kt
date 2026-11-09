package com.aimbot.runner

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
                addToggle("Aimbot enabled", config.optBoolean("aimbot", true))
                addToggle("Auto fire", config.optBoolean("autoFire", false))
                addToggle("Visibility check", config.optBoolean("visibilityCheck", true))
                addSeekBar("FOV", config.optInt("fov", 90), 30, 120)
                addSeekBar("Smooth", config.optDouble("smooth", 1.0).toFloat(), 0.5f, 3.0f)
                addSeekBar("Aim FOV", config.optInt("aimFov", 80), 30, 120)
                addSpinner("Bone", arrayOf("Head", "Neck", "Chest"), 0)
            }
            "esp" -> {
                addToggle("Box", config.optBoolean("box", true))
                addToggle("Skeleton", config.optBoolean("skeleton", true))
                addToggle("Distance", config.optBoolean("distance", true))
                addToggle("Health", config.optBoolean("health", true))
                addToggle("Name", config.optBoolean("name", true))
                addToggle("Snapline", config.optBoolean("snapline", true))
                if (config.has("imposterHighlight")) {
                    addToggle("Imposter highlight", config.optBoolean("imposterHighlight", false))
                }
                if (config.has("xray")) {
                    addToggle("X-ray", config.optBoolean("xray", false))
                }
            }
            "misc" -> {
                if (config.has("fly")) addToggle("Fly", config.optBoolean("fly", false))
                if (config.has("speed")) addToggle("Speed", config.optBoolean("speed", false))
                if (config.has("godmode")) addToggle("God mode", config.optBoolean("godmode", false))
                if (config.has("nocrecoil")) addToggle("No recoil", config.optBoolean("nocrecoil", true))
                if (config.has("killrange")) addToggle("Kill range", config.optBoolean("killrange", false))
                if (config.has("coinmagnet")) addToggle("Coin magnet", config.optBoolean("coinmagnet", false))
                if (config.has("autoplay")) addToggle("Auto play", config.optBoolean("autoplay", false))
                if (config.has("mult")) {
                    addSeekBar("Multiplier", config.optDouble("mult", 2.0).toFloat(), 1.0f, 10.0f)
                }
            }
        }
    }

    private fun addToggle(label: String, checked: Boolean) {
        val cb = CheckBox(this)
        cb.text = label
        cb.isChecked = checked
        cb.setTextColor(0xFFFFFFFF)
        binding.container.addView(cb)
    }

    private fun addSeekBar(label: String, value: Float, min: Float, max: Float) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val tv = TextView(this)
        tv.text = "$label: $value"
        tv.textColor = 0xFFFFFFFF
        tv.layoutParams = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT)
        val sb = SeekBar(this)
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

    private fun addSpinner(label: String, items: Array<String>, selected: Int) {
        val tv = TextView(this)
        tv.text = label
        tv.textColor = 0xFFFFFFFF
        binding.container.addView(tv)
        val sp = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.adapter = adapter
        sp.setSelection(selected)
        binding.container.addView(sp)
    }

    private fun saveFeatures() {
        // In a full build this writes back to assets/scripts.json per-game.
        // Here we keep config in memory for the current session.
        val count = binding.container.childCount
        for (i in 0 until count) {
            val v = binding.container.getChildAt(i)
            if (v is CheckBox) {
                config.put(v.text.toString(), v.isChecked)
            }
        }
    }
}
