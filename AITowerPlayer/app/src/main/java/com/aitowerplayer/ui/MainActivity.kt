package com.aitowerplayer.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aitowerplayer.R
import com.aitowerplayer.databinding.ActivityMainBinding
import com.aitowerplayer.model.GameState
import com.aitowerplayer.service.FloatingControlService
import com.aitowerplayer.service.ScreenCaptureService
import com.aitowerplayer.service.TowerAccessibilityService
import com.aitowerplayer.util.ConfigManager
import com.aitowerplayer.util.LogManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaProjectionResultCode = 0
    private var mediaProjectionData: Intent? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            mediaProjectionResultCode = result.resultCode
            mediaProjectionData = result.data
            updateStatusIndicators()
            LogManager.log("Izin Screen Capture diberikan.")
            Toast.makeText(this, "Screen Capture OK. Tekan START AI.", Toast.LENGTH_SHORT).show()
        } else {
            LogManager.log("Izin Screen Capture ditolak.")
            Toast.makeText(this, "Izin Screen Capture diperlukan!", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingControlService::class.java))
        }
    }

    private val logListener: (String) -> Unit = { line ->
        runOnUiThread {
            if (line.isEmpty()) {
                binding.tvLog.text = ""
            } else {
                binding.tvLog.append("$line\n")
                binding.scrollLog.post {
                    binding.scrollLog.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        LogManager.addListener(logListener)
        binding.tvLog.text = LogManager.getAll() + "\n"

        ScreenCaptureService.stateListener = { state, msg ->
            runOnUiThread {
                binding.tvCurrentState.text = state.name
                binding.tvAIStatus.text = "AI: ${state.name}"
                updateStats()
                updateButtons()
            }
        }
    }

    private fun setupUI() {
        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Aktifkan 'AI Tower Player' di daftar Accessibility", Toast.LENGTH_LONG).show()
        }

        binding.btnRequestCapture.setOnClickListener {
            requestScreenCapture()
        }

        binding.btnStart.setOnClickListener {
            startAi()
        }

        binding.btnStop.setOnClickListener {
            stopAi()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnTaskManager.setOnClickListener {
            startActivity(Intent(this, TaskManagerActivity::class.java))
        }

        binding.btnFloating.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                startService(Intent(this, FloatingControlService::class.java))
                Toast.makeText(this, "Floating control aktif", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearLog.setOnClickListener {
            LogManager.clear()
            binding.tvLog.text = ""
        }
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun startAi() {
        if (!TowerAccessibilityService.isRunning()) {
            Toast.makeText(this, "Aktifkan Accessibility Service dulu!", Toast.LENGTH_LONG).show()
            return
        }
        if (mediaProjectionData == null) {
            Toast.makeText(this, "Minta izin Screen Capture dulu!", Toast.LENGTH_LONG).show()
            requestScreenCapture()
            return
        }
        if (ScreenCaptureService.isRunning) {
            Toast.makeText(this, "AI sudah berjalan", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, mediaProjectionResultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, mediaProjectionData)
        }
        ContextCompat.startForegroundService(this, intent)
        LogManager.log("START AI dikirim.")
        updateButtons()
    }

    private fun stopAi() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(intent)
        LogManager.log("STOP dikirim.")
        updateButtons()
    }

    private fun updateStatusIndicators() {
        val accOn = TowerAccessibilityService.isRunning()
        binding.indicatorAccessibility.setBackgroundResource(
            if (accOn) R.drawable.status_indicator_on else R.drawable.status_indicator_off
        )

        val captureOn = mediaProjectionData != null
        binding.indicatorScreenCapture.setBackgroundResource(
            if (captureOn) R.drawable.status_indicator_on else R.drawable.status_indicator_off
        )

        val aiOn = ScreenCaptureService.isRunning
        binding.indicatorAI.setBackgroundResource(
            when {
                aiOn -> R.drawable.status_indicator_on
                else -> R.drawable.status_indicator_off
            }
        )
        binding.tvAIStatus.text = if (aiOn) "AI: ${ScreenCaptureService.currentState.name}" else "AI: Idle"
        binding.tvCurrentState.text = ScreenCaptureService.currentState.name
    }

    private fun updateButtons() {
        val running = ScreenCaptureService.isRunning
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled = running
    }

    private fun updateStats() {
        binding.tvLevelsCompleted.text = ConfigManager.levelsCompleted.toString()
        binding.tvWins.text = ConfigManager.wins.toString()
        binding.tvFails.text = ConfigManager.fails.toString()
    }

    override fun onResume() {
        super.onResume()
        updateStatusIndicators()
        updateStats()
        updateButtons()
    }

    override fun onDestroy() {
        LogManager.removeListener(logListener)
        ScreenCaptureService.stateListener = null
        super.onDestroy()
    }
}
