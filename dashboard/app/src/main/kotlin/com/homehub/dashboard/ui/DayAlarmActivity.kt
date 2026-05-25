package com.homehub.dashboard.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.homehub.dashboard.HomeHubApp
import com.homehub.dashboard.data.AlarmEntity
import com.homehub.dashboard.databinding.ActivityDayAlarmBinding
import kotlinx.coroutines.launch

class DayAlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDayAlarmBinding
    private lateinit var app: HomeHubApp
    private val adapter = AlarmListAdapter(
        onToggle = { alarm, checked -> toggleAlarm(alarm, checked) },
        onClick = { alarm ->
            startActivity(Intent(this, EditAlarmActivity::class.java).putExtra(EditAlarmActivity.EXTRA_ALARM_ID, alarm.id))
        }
    )

    private val weekday: Int by lazy { intent.getIntExtra(EXTRA_WEEKDAY, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDayAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as HomeHubApp

        binding.tvTitle.text = intent.getStringExtra(EXTRA_DAY_NAME) ?: "ALARMS"
        binding.recyclerAlarms.layoutManager = LinearLayoutManager(this)
        binding.recyclerAlarms.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddAlarm.setOnClickListener {
            startActivity(Intent(this, EditAlarmActivity::class.java).putExtra(EditAlarmActivity.EXTRA_WEEKDAY, weekday))
        }

        lifecycleScope.launch {
            app.alarmRepository.observeByWeekday(weekday).collect { alarms ->
                adapter.submitList(alarms)
                binding.tvEmpty.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun toggleAlarm(alarm: AlarmEntity, checked: Boolean) {
        lifecycleScope.launch {
            val updated = alarm.copy(enabled = checked)
            app.alarmRepository.update(updated)
            if (checked) app.alarmScheduler.schedule(updated) else app.alarmScheduler.cancel(updated.id)
        }
    }

    companion object {
        const val EXTRA_WEEKDAY = "weekday"
        const val EXTRA_DAY_NAME = "day_name"
    }
}
