package com.homehub.dashboard.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.homehub.dashboard.HomeHubApp
import com.homehub.dashboard.R
import com.homehub.dashboard.alarm.AlarmReceiver
import com.homehub.dashboard.databinding.ActivityAlarmRingingBinding
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min

class AlarmRingingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmRingingBinding
    private lateinit var app: HomeHubApp
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rampRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        binding = ActivityAlarmRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as HomeHubApp

        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L)
        lifecycleScope.launch {
            val alarm = app.alarmRepository.getById(alarmId) ?: run {
                finish()
                return@launch
            }
            binding.tvAlarmTime.text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
            binding.tvAlarmLabel.text = alarm.label.ifBlank { getString(R.string.default_alarm_label) }
            playAlarm(alarm.soundUri, alarm.fadeInSeconds, alarm.targetVolume)
            binding.btnDismiss.setOnClickListener {
                stopPlayback()
                finish()
            }
            binding.btnSnooze.setOnClickListener {
                app.alarmScheduler.scheduleSnooze(alarm, alarm.snoozeMinutes)
                stopPlayback()
                finish()
            }
        }
    }

    private fun playAlarm(uriString: String?, fadeInSeconds: Int, targetVolume: Int) {
        val source = uriString ?: return
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(this@AlarmRingingActivity, android.net.Uri.parse(source))
            isLooping = true
            prepare()
            setVolume(0f, 0f)
            start()
        }
        val steps = maxOf(1, fadeInSeconds * 2)
        val target = targetVolume / 100f
        var currentStep = 0
        rampRunnable = object : Runnable {
            override fun run() {
                currentStep += 1
                val volume = min(target, target * currentStep / steps)
                mediaPlayer?.setVolume(volume, volume)
                if (currentStep < steps) {
                    handler.postDelayed(this, 500L)
                }
            }
        }.also(handler::post)
    }

    private fun stopPlayback() {
        rampRunnable?.let(handler::removeCallbacks)
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
