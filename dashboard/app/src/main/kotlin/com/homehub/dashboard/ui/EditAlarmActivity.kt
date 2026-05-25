package com.homehub.dashboard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.homehub.dashboard.HomeHubApp
import com.homehub.dashboard.data.AlarmEntity
import com.homehub.dashboard.databinding.ActivityEditAlarmBinding
import kotlinx.coroutines.launch

class EditAlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditAlarmBinding
    private lateinit var app: HomeHubApp
    private var currentAlarm: AlarmEntity? = null
    private var selectedSoundUri: String? = null

    private val pickSound = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selectedSoundUri = uri.toString()
            binding.tvSoundValue.text = uri.lastPathSegment ?: uri.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as HomeHubApp

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickSound.setOnClickListener { pickSound.launch(arrayOf("audio/*", "video/*")) }
        binding.btnSave.setOnClickListener { saveAlarm() }
        binding.btnDelete.setOnClickListener { deleteAlarm() }
        binding.timePicker.setIs24HourView(true)

        lifecycleScope.launch {
            val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
            val defaults = app.settingsRepository.get()
            if (alarmId > 0L) {
                currentAlarm = app.alarmRepository.getById(alarmId)
                currentAlarm?.let(::bindAlarm)
            } else {
                binding.timePicker.hour = 7
                binding.timePicker.minute = 0
                binding.etLabel.setText("")
                binding.switchEnabled.isChecked = true
                binding.switchRepeat.isChecked = true
                binding.seekFade.progress = defaults.defaultAlarmFadeSeconds
                binding.seekVolume.progress = defaults.defaultTargetVolume
                binding.seekSnooze.progress = defaults.defaultSnoozeMinutes
                selectedSoundUri = defaults.defaultSoundUri
                binding.tvSoundValue.text = selectedSoundUri ?: "No sound selected"
            }
        }
    }

    private fun bindAlarm(alarm: AlarmEntity) {
        binding.timePicker.hour = alarm.hour
        binding.timePicker.minute = alarm.minute
        binding.etLabel.setText(alarm.label)
        binding.switchEnabled.isChecked = alarm.enabled
        binding.switchRepeat.isChecked = alarm.repeatWeekly
        binding.seekFade.progress = alarm.fadeInSeconds
        binding.seekVolume.progress = alarm.targetVolume
        binding.seekSnooze.progress = alarm.snoozeMinutes
        selectedSoundUri = alarm.soundUri
        binding.tvSoundValue.text = selectedSoundUri ?: "No sound selected"
    }

    private fun saveAlarm() {
        lifecycleScope.launch {
            val defaults = app.settingsRepository.get()
            val alarm = AlarmEntity(
                id = currentAlarm?.id ?: 0,
                weekday = currentAlarm?.weekday ?: intent.getIntExtra(EXTRA_WEEKDAY, 0),
                hour = binding.timePicker.hour,
                minute = binding.timePicker.minute,
                label = binding.etLabel.text?.toString().orEmpty(),
                enabled = binding.switchEnabled.isChecked,
                repeatWeekly = binding.switchRepeat.isChecked,
                soundUri = selectedSoundUri ?: defaults.defaultSoundUri,
                fadeInSeconds = binding.seekFade.progress.coerceAtLeast(1),
                targetVolume = binding.seekVolume.progress.coerceIn(1, 100),
                snoozeMinutes = binding.seekSnooze.progress.coerceAtLeast(1)
            )
            val id = if (currentAlarm == null) app.alarmRepository.insert(alarm) else {
                app.alarmRepository.update(alarm)
                alarm.id
            }
            val savedAlarm = alarm.copy(id = id)
            if (savedAlarm.enabled) app.alarmScheduler.schedule(savedAlarm) else app.alarmScheduler.cancel(savedAlarm.id)
            Toast.makeText(this@EditAlarmActivity, "Alarm saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun deleteAlarm() {
        val alarm = currentAlarm ?: run {
            finish()
            return
        }
        lifecycleScope.launch {
            app.alarmRepository.delete(alarm)
            app.alarmScheduler.cancel(alarm.id)
            Toast.makeText(this@EditAlarmActivity, "Alarm deleted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_WEEKDAY = "weekday"
    }
}
