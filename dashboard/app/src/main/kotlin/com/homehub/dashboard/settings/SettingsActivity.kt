package com.homehub.dashboard.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.homehub.dashboard.HomeHubApp
import com.homehub.dashboard.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var app: HomeHubApp
    private var selectedSoundUri: String? = null

    private val pickSound = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selectedSoundUri = uri.toString()
            binding.tvDefaultSound.text = uri.lastPathSegment ?: uri.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as HomeHubApp

        binding.tvVersion.text = "v${com.homehub.dashboard.BuildConfig.VERSION_NAME} (${com.homehub.dashboard.BuildConfig.VERSION_CODE})"

        val settings = app.settingsRepository.get()
        binding.btnBack.setOnClickListener { finish() }
        binding.etLocationName.setText(settings.locationName)
        binding.etLatitude.setText(settings.latitude.toString())
        binding.etLongitude.setText(settings.longitude.toString())
        binding.etHubHost.setText(settings.hubHost)
        binding.seekIdleBrightness.progress = settings.idleBrightness
        binding.seekActiveBrightness.progress = settings.activeBrightness
        binding.seekIdleTimeout.progress = settings.idleTimeoutSeconds
        binding.seekBrightnessFade.progress = settings.brightnessFadeMillis / 100
        binding.switchFullBlack.isChecked = settings.useFullBlackIdle
        binding.seekDefaultFade.progress = settings.defaultAlarmFadeSeconds
        binding.seekDefaultVolume.progress = settings.defaultTargetVolume
        binding.seekDefaultSnooze.progress = settings.defaultSnoozeMinutes
        binding.tvDefaultSound.text = settings.defaultSoundUri ?: "No sound selected"
        selectedSoundUri = settings.defaultSoundUri

        binding.btnPickDefaultSound.setOnClickListener { pickSound.launch(arrayOf("audio/*", "video/*")) }
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnBattery.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val latitude = binding.etLatitude.text?.toString()?.toDoubleOrNull()
        val longitude = binding.etLongitude.text?.toString()?.toDoubleOrNull()
        if (latitude == null || longitude == null) {
            Toast.makeText(this, "Latitude and longitude must be valid numbers", Toast.LENGTH_SHORT).show()
            return
        }
        app.settingsRepository.update {
            it.copy(
                locationName = binding.etLocationName.text?.toString().orEmpty().ifBlank { "Hub" },
                latitude = latitude,
                longitude = longitude,
                hubHost = binding.etHubHost.text?.toString().orEmpty().ifBlank { "127.0.0.1" },
                idleBrightness = binding.seekIdleBrightness.progress,
                activeBrightness = binding.seekActiveBrightness.progress.coerceAtLeast(1),
                idleTimeoutSeconds = binding.seekIdleTimeout.progress.coerceAtLeast(5),
                brightnessFadeMillis = binding.seekBrightnessFade.progress.coerceAtLeast(1) * 100,
                useFullBlackIdle = binding.switchFullBlack.isChecked,
                defaultAlarmFadeSeconds = binding.seekDefaultFade.progress.coerceAtLeast(1),
                defaultTargetVolume = binding.seekDefaultVolume.progress.coerceIn(1, 100),
                defaultSnoozeMinutes = binding.seekDefaultSnooze.progress.coerceAtLeast(1),
                defaultSoundUri = selectedSoundUri
            )
        }
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
