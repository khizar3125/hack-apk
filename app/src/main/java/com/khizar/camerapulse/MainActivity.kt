package com.khizar.camerapulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), HeartRateAnalyzer.Listener {

    private lateinit var analyzer: HeartRateAnalyzer
    private lateinit var historyStore: HistoryStore
    private lateinit var historyAdapter: HistoryAdapter

    private lateinit var introScreen: View
    private lateinit var measureScreen: View
    private lateinit var resultScreen: View
    private lateinit var previewView: PreviewView
    private lateinit var liveBpmText: TextView
    private lateinit var statusText: TextView
    private lateinit var countdownText: TextView
    private lateinit var progressRing: ProgressRingView
    private lateinit var waveformView: WaveformView
    private lateinit var resultBpmText: TextView
    private lateinit var zoneChip: TextView
    private lateinit var resultHeart: PulseView
    private lateinit var introHeart: PulseView
    private lateinit var historyList: RecyclerView
    private lateinit var emptyHistoryText: TextView

    private var measuring = false
    private var liveBpmSeen = false
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                beginMeasurement()
            } else {
                Toast.makeText(this, getString(R.string.camera_needed), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        analyzer = HeartRateAnalyzer(this)
        historyStore = HistoryStore(this)
        historyAdapter = HistoryAdapter()

        introScreen = findViewById(R.id.introScreen)
        measureScreen = findViewById(R.id.measureScreen)
        resultScreen = findViewById(R.id.resultScreen)
        previewView = findViewById(R.id.previewView)
        liveBpmText = findViewById(R.id.liveBpmText)
        statusText = findViewById(R.id.statusText)
        countdownText = findViewById(R.id.countdownText)
        progressRing = findViewById(R.id.progressRing)
        waveformView = findViewById(R.id.waveformView)
        resultBpmText = findViewById(R.id.resultBpm)
        zoneChip = findViewById(R.id.zoneChip)
        resultHeart = findViewById(R.id.resultHeart)
        introHeart = findViewById(R.id.introHeart)
        historyList = findViewById(R.id.historyList)
        emptyHistoryText = findViewById(R.id.emptyHistoryText)

        historyList.layoutManager = LinearLayoutManager(this)
        historyList.adapter = historyAdapter

        findViewById<TextView>(R.id.startButton).setOnClickListener { checkPermissionAndStart() }
        findViewById<TextView>(R.id.measureAgainButton).setOnClickListener { checkPermissionAndStart() }
        findViewById<TextView>(R.id.doneButton).setOnClickListener { showIntro() }
        findViewById<TextView>(R.id.cancelButton).setOnClickListener { cancelMeasurement() }
        findViewById<TextView>(R.id.clearHistoryButton).setOnClickListener {
            historyStore.clear()
            refreshHistory()
        }

        refreshHistory()
        showIntro()
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            beginMeasurement()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun beginMeasurement() {
        measuring = true
        liveBpmSeen = false
        introScreen.visibility = View.GONE
        resultScreen.visibility = View.GONE
        measureScreen.visibility = View.VISIBLE
        introHeart.stop()
        resultHeart.stop()

        analyzer.reset()
        waveformView.clear()
        liveBpmText.text = "-"
        liveBpmText.scaleX = 1f
        liveBpmText.scaleY = 1f
        progressRing.setProgress(0f)
        countdownText.text = getString(R.string.seconds_left, MEASURE_SECONDS)
        statusText.text = getString(R.string.place_finger)

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!measuring) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(cameraExecutor, analyzer)

                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
                camera?.cameraControl?.enableTorch(true)
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Camera error", Toast.LENGTH_LONG).show()
                showIntro()
            }
        }, ContextCompat.getMainExecutor(this))

        scope.launch {
            for (elapsed in 1..MEASURE_SECONDS) {
                delay(1000)
                if (!measuring || !isActive) return@launch
                progressRing.setProgress(elapsed.toFloat() / MEASURE_SECONDS)
                countdownText.text = getString(R.string.seconds_left, MEASURE_SECONDS - elapsed)
            }
            finishMeasurement()
        }
    }

    private fun finishMeasurement() {
        val bpm = analyzer.stableBpm()
        val confidence = analyzer.lastConfidence
        stopCamera()

        if (bpm == null || confidence < 0.10f) {
            Toast.makeText(this, getString(R.string.weak_signal), Toast.LENGTH_LONG).show()
            showIntro()
            return
        }

        historyStore.add(bpm)
        refreshHistory()
        showResult(bpm)
    }

    private fun cancelMeasurement() {
        stopCamera()
        showIntro()
    }

    private fun stopCamera() {
        measuring = false
        try {
            camera?.cameraControl?.enableTorch(false)
        } catch (_: Exception) {
        }
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        camera = null
    }

    private fun showIntro() {
        measuring = false
        introHeart.stop()
        resultHeart.stop()
        measureScreen.visibility = View.GONE
        resultScreen.visibility = View.GONE
        introScreen.visibility = View.VISIBLE
        introHeart.start()
    }

    private fun showResult(bpm: Int) {
        measureScreen.visibility = View.GONE
        introScreen.visibility = View.GONE
        resultScreen.visibility = View.VISIBLE

        resultBpmText.text = bpm.toString()
        val (label, color) = when {
            bpm < 60 -> getString(R.string.zone_resting) to 0xFF4ADE80.toInt()
            bpm <= 100 -> getString(R.string.zone_normal) to 0xFF4ADE80.toInt()
            bpm <= 120 -> getString(R.string.zone_elevated) to 0xFFFBBF24.toInt()
            else -> getString(R.string.zone_high) to 0xFFF87171.toInt()
        }
        resultBpmText.setTextColor(color)
        zoneChip.text = label
        zoneChip.setTextColor(color)

        resultHeart.setBpm(bpm)
        resultHeart.start()
    }

    private fun refreshHistory() {
        val readings = historyStore.all()
        historyAdapter.submit(readings)
        emptyHistoryText.visibility = if (readings.isEmpty()) View.VISIBLE else View.GONE
        historyList.visibility = if (readings.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onWaveform(normalized: Float) {
        runOnUiThread { waveformView.addSample(normalized) }
    }

    override fun onBeat() {
        runOnUiThread {
            if (measuring) {
                liveBpmText.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90)
                    .withEndAction {
                        liveBpmText.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                    }.start()
            }
        }
    }

    override fun onBpm(bpm: Int, confidence: Float) {
        runOnUiThread {
            if (measuring) {
                liveBpmSeen = true
                liveBpmText.text = bpm.toString()
            }
        }
    }

    override fun onFingerPresence(present: Boolean) {
        runOnUiThread {
            if (measuring) {
                statusText.text = if (!present) {
                    getString(R.string.place_finger)
                } else {
                    getString(R.string.detecting)
                }
            }
        }
    }

    override fun onDestroy() {
        measuring = false
        stopCamera()
        cameraExecutor.shutdownNow()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val MEASURE_SECONDS = 30
    }
}
