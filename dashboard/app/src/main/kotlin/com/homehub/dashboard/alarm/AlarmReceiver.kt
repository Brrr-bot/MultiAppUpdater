package com.homehub.dashboard.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.homehub.dashboard.HomeHubApp
import com.homehub.dashboard.ui.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as HomeHubApp
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                val alarm = app.alarmRepository.getById(alarmId)
                if (alarm != null) {
                    if (!intent.getBooleanExtra(EXTRA_IS_SNOOZE, false) && alarm.repeatWeekly && alarm.enabled) {
                        app.alarmScheduler.schedule(alarm)
                    }
                    val ringIntent = Intent(context, AlarmRingingActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(EXTRA_ALARM_ID, alarm.id)
                    context.startActivity(ringIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_IS_SNOOZE = "is_snooze"
    }
}
