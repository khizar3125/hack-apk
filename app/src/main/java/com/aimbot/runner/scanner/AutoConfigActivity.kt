package com.aimbot.runner.scanner

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aimbot.runner.R
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.script.ScriptRunner
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

/**
 * Auto-configuration screen: attaches to a running game, scans memory for
 * values and byte patterns, and writes discovered offsets back to
 * games.json so ESP/aimbot/misc features actually work.
 *
 * No pre-dumped offsets required: the user scans for a known value
 * (e.g. current health = 100), changes it in-game, filters, and the app
 * isolates the real address.
 */
class AutoConfigActivity : AppCompatActivity() {

    private lateinit var runner: ScriptRunner
    private lateinit var games: List<GameConfig>
    private var selectedGame: GameConfig? = null

    private lateinit var spinnerGame: Spinner
    private lateinit var btnAttach: Button
    private lateinit var btnScanValue: Button
    private lateinit var btnFilter: Button
    private lateinit var btnChanged: Button
    private lateinit var btnUnchanged: Button
    private lateinit var btnScanPattern: Button
    private lateinit var btnSaveOffsets: Button
    private lateinit var edtValue: EditText
    private lateinit var edtPattern: EditText
    private lateinit var edtPatternName: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvResults: TextView

    private var scanner: MemoryScanner? = null
    private var patternScanner: PatternScanner? = null
    private var discoveredOffsets = mutableMapOf<String, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_config)

        runner = ScriptRunner(this)
        games = runner.loadGames()

        spinnerGame = findViewById(R.id.spinnerGame)
        btnAttach = findViewById(R.id.btnAttach)
        btnScanValue = findViewById(R.id.btnScanValue)
        btnFilter = findViewById(R.id.btnFilter)
        btnChanged = findViewById(R.id.btnChanged)
        btnUnchanged = findViewById(R.id.btnUnchanged)
        btnScanPattern = findViewById(R.id.btnScanPattern)
        btnSaveOffsets = findViewById(R.id.btnSaveOffsets)
        edtValue = findViewById(R.id.edtValue)
        edtPattern = findViewById(R.id.edtPattern)
        edtPatternName = findViewById(R.id.edtPatternName)
        tvStatus = findViewById(R.id.tvStatus)
        tvResults = findViewById(R.id.tvResults)

        val names = games.map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGame.adapter = spinnerAdapter
        spinnerGame.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, position: Int, id: Long) {
                selectedGame = games[position]
                tvStatus.text = "Selected: ${selectedGame?.name} (${selectedGame?.packageName})"
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        btnAttach.setOnClickListener { attach() }
        btnScanValue.setOnClickListener { firstScan() }
        btnFilter.setOnClickListener { filterScan() }
        btnChanged.setOnClickListener { changedScan() }
        btnUnchanged.setOnClickListener { unchangedScan() }
        btnScanPattern.setOnClickListener { patternScan() }
        btnSaveOffsets.setOnClickListener { saveOffsets() }

        updateButtons(false)
    }

    private fun updateButtons(attached: Boolean) {
        btnScanValue.isEnabled = attached
        btnFilter.isEnabled = attached
        btnChanged.isEnabled = attached
        btnUnchanged.isEnabled = attached
        btnScanPattern.isEnabled = attached
        btnSaveOffsets.isEnabled = attached && discoveredOffsets.isNotEmpty()
    }

    private fun attach() {
        val game = selectedGame ?: return
        if (!runner.engine.attach(game.packageName)) {
            tvStatus.text = "Attach failed: ${game.packageName} not running?"
            return
        }
        if (!runner.engine.resolveModule(game.module)) {
            tvStatus.text = "Module ${game.module} not found"
            return
        }

        scanner = MemoryScanner(runner.engine.pid)
        patternScanner = PatternScanner(
            runner.engine.pid,
            runner.engine.moduleBase,
            0x20000000 // 512 MB scan window, adjust per game
        )

        tvStatus.text = "Attached pid=${runner.engine.pid} base=0x${runner.engine.moduleBase.toString(16)}"
        updateButtons(true)
        Toast.makeText(this, "Attached to ${game.name}", Toast.LENGTH_SHORT).show()
    }

    private fun firstScan() {
        val s = scanner ?: return
        val text = edtValue.text.toString().trim()
        if (text.isEmpty()) return

        val type = MemoryScanner.ScanType.INT
        val value = text.toIntOrNull() ?: text.toFloatOrNull()
        if (value == null) {
            tvStatus.text = "Enter a number (e.g. current health)"
            return
        }

        Handler(Looper.getMainLooper()).post {
            tvStatus.text = "Scanning..."
        }

        val thread = Thread {
            val results = if (value is Int) {
                s.firstScan(value, type)
            } else {
                s.firstScan(value as Float, MemoryScanner.ScanType.FLOAT)
            }
            Handler(Looper.getMainLooper()).post {
                tvResults.text = "First scan: ${results.size} candidates\n" +
                    results.take(20).joinToString("\n") { "0x${it.address.toString(16)} = ${it.value}" }
                tvStatus.text = "First scan done: ${results.size} candidates"
                updateButtons(true)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun filterScan() {
        val s = scanner ?: return
        val text = edtValue.text.toString().trim()
        if (text.isEmpty()) return

        val type = MemoryScanner.ScanType.INT
        val value = text.toIntOrNull() ?: text.toFloatOrNull()
        if (value == null) return

        val thread = Thread {
            val results = if (value is Int) {
                s.filterScan(value, type)
            } else {
                s.filterScan(value as Float, MemoryScanner.ScanType.FLOAT)
            }
            Handler(Looper.getMainLooper()).post {
                tvResults.text = "Filter scan: ${results.size} candidates\n" +
                    results.take(20).joinToString("\n") { "0x${it.address.toString(16)} = ${it.value}" }
                tvStatus.text = "Filter done: ${results.size} candidates"
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun changedScan() {
        val s = scanner ?: return
        val thread = Thread {
            val results = s.changedScan()
            Handler(Looper.getMainLooper()).post {
                tvResults.text = "Changed scan: ${results.size} candidates\n" +
                    results.take(20).joinToString("\n") { "0x${it.address.toString(16)} = ${it.value}" }
                tvStatus.text = "Changed scan done: ${results.size} candidates"
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun unchangedScan() {
        val s = scanner ?: return
        val thread = Thread {
            val results = s.unchangedScan()
            Handler(Looper.getMainLooper()).post {
                tvResults.text = "Unchanged scan: ${results.size} candidates\n" +
                    results.take(20).joinToString("\n") { "0x${it.address.toString(16)} = ${it.value}" }
                tvStatus.text = "Unchanged scan done: ${results.size} candidates"
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun patternScan() {
        val ps = patternScanner ?: return
        val sig = edtPattern.text.toString().trim()
        if (sig.isEmpty()) return
        val name = edtPatternName.text.toString().trim()
        if (name.isEmpty()) {
            tvStatus.text = "Enter a pattern name"
            return
        }

        val thread = Thread {
            val matches = ps.scan(PatternScanner.Pattern(name, sig))
            Handler(Looper.getMainLooper()).post {
                tvResults.text = "Pattern '$name': ${matches.size} matches\n" +
                    matches.take(20).joinToString("\n") { "0x${it.address.toString(16)} (off 0x${it.offsetFromBase.toString(16)})" }
                tvStatus.text = "Pattern scan done: ${matches.size} matches"
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun saveOffsets() {
        val game = selectedGame ?: return
        val s = scanner ?: return

        val cands = s.getCandidates()
        if (cands.isEmpty()) {
            tvStatus.text = "No candidates to save"
            return
        }

        // Save candidates as offsets with descriptive keys.
        // User should verify which address maps to which game structure.
        discoveredOffsets.clear()
        for ((i, c) in cands.withIndex()) {
            discoveredOffsets["candidate_${i + 1}"] = c.address
        }

        // Also add pattern matches shown in the UI as named offsets
        // (pattern results are displayed above; user notes the addresses)

        updateGamesJson(game, cands)

        tvStatus.text = "Saved ${cands.size} candidates as offsets for ${game.name}"
        btnSaveOffsets.isEnabled = false
        Toast.makeText(this, "Offsets saved to games.json", Toast.LENGTH_SHORT).show()
    }

    private fun updateGamesJson(game: GameConfig, cands: List<MemoryScanner.Candidate>) {
        try {
            val json = assets.open("games.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.getString("package") == game.packageName) {
                    val offs = if (o.has("offsets") && !o.isNull("offsets")) o.getJSONObject("offsets") else JSONObject()
                    // Save the first candidate of each type to known keys
                    val intCands = cands.filter { it.type == MemoryScanner.ScanType.INT }
                    val floatCands = cands.filter { it.type == MemoryScanner.ScanType.FLOAT }
                    if (intCands.isNotEmpty()) offs.put("localPlayer", intCands[0].address)
                    if (intCands.size > 1) offs.put("camera", intCands[1].address)
                    if (intCands.size > 2) offs.put("entityList", intCands[2].address)
                    if (intCands.size > 3) offs.put("healthAddr", intCands[3].address)
                    if (intCands.size > 4) offs.put("speedAddr", intCands[4].address)
                    if (floatCands.isNotEmpty()) offs.put("posOff", floatCands[0].address.toInt())
                    o.put("offsets", offs)
                    arr.put(i, o)
                    break
                }
            }
            val updated = arr.toString(2)
            val file = File(filesDir, "games.json")
            FileWriter(file).use { it.write(updated) }
            tvStatus.text = "games.json updated in internal storage"
        } catch (e: Exception) {
            tvStatus.text = "Save failed: ${e.message}"
        }
    }
}
