package com.aitowerplayer.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aitowerplayer.databinding.ActivitySettingsBinding
import com.aitowerplayer.model.AppConfig
import com.aitowerplayer.util.ConfigManager
import com.aitowerplayer.util.LogManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Pengaturan"

        loadCurrent()
        setupSliders()

        binding.btnSaveSettings.setOnClickListener {
            save()
        }
    }

    private fun loadCurrent() {
        val c = ConfigManager.config
        binding.sliderDetectionLine.value = c.detectionLinePercent
        binding.tvDetectionLine.text = "${c.detectionLinePercent.toInt()}%"

        binding.sliderBlackThreshold.value = c.blackThreshold.toFloat()
        binding.tvBlackThreshold.text = c.blackThreshold.toString()

        binding.sliderPixelThreshold.value = c.pixelThresholdPercent
        binding.tvPixelThreshold.text = "${c.pixelThresholdPercent.toInt()}%"

        binding.sliderBandHeight.value = c.bandHeightPx.toFloat()
        binding.tvBandHeight.text = c.bandHeightPx.toString()

        binding.sliderFps.value = c.analysisFps.toFloat()
        binding.tvFps.text = c.analysisFps.toString()

        binding.sliderAnimWait.value = c.animationWaitMs.toFloat()
        binding.tvAnimWait.text = c.animationWaitMs.toString()

        binding.switchAutoLevel.isChecked = c.autoLevelEnabled
        binding.switchOcr.isChecked = c.useOcr
    }

    private fun setupSliders() {
        binding.sliderDetectionLine.addOnChangeListener { _, value, _ ->
            binding.tvDetectionLine.text = "${value.toInt()}%"
        }
        binding.sliderBlackThreshold.addOnChangeListener { _, value, _ ->
            binding.tvBlackThreshold.text = value.toInt().toString()
        }
        binding.sliderPixelThreshold.addOnChangeListener { _, value, _ ->
            binding.tvPixelThreshold.text = "${value.toInt()}%"
        }
        binding.sliderBandHeight.addOnChangeListener { _, value, _ ->
            binding.tvBandHeight.text = value.toInt().toString()
        }
        binding.sliderFps.addOnChangeListener { _, value, _ ->
            binding.tvFps.text = value.toInt().toString()
        }
        binding.sliderAnimWait.addOnChangeListener { _, value, _ ->
            binding.tvAnimWait.text = value.toInt().toString()
        }
    }

    private fun save() {
        val newConfig = AppConfig(
            detectionLinePercent = binding.sliderDetectionLine.value,
            blackThreshold = binding.sliderBlackThreshold.value.toInt(),
            pixelThresholdPercent = binding.sliderPixelThreshold.value,
            bandHeightPx = binding.sliderBandHeight.value.toInt(),
            analysisFps = binding.sliderFps.value.toInt(),
            animationWaitMs = binding.sliderAnimWait.value.toLong(),
            touchXPercent = ConfigManager.config.touchXPercent,
            touchYPercent = ConfigManager.config.touchYPercent,
            autoLevelEnabled = binding.switchAutoLevel.isChecked,
            useOcr = binding.switchOcr.isChecked
        )
        ConfigManager.saveConfig(newConfig)
        LogManager.log("Pengaturan disimpan.")
        Toast.makeText(this, "Pengaturan disimpan", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
