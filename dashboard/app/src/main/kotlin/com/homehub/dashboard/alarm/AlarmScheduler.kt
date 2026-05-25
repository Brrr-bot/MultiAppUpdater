package com.homehub.dashboard.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.homehub.dashboard.data.AlarmEntity
import com.homehub.dashboard.data.AlarmRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmScheduler(
    private val context: Context,
    private val alarmRepository: AlarmRepository
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val triggerAt = nextTriggerTime(alarm)
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(alarmId: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    suspend fun rescheduleAll() {
        alarmRepository.getEnabled().forEach(::schedule)
    }

    fun scheduleSnooze(alarm: AlarmEntity, snoozeMinutes: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            .putExtra(AlarmReceiver.EXTRA_IS_SNOOZE, true)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (alarm.id + 100_000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + snoozeMinutes * 60_000L,
            pendingIntent
        )
    }

    private fun nextTriggerTime(alarm: AlarmEntity): Long {
        val now = LocalDateTime.now()
        val targetDay = weekdayToDayOfWeek(alarm.weekday)
        var candidate = now
            .withHour(alarm.hour)
            .withMinute(alarm.minute)
            .withSecond(0)
            .withNano(0)
        while (candidate.dayOfWeek != targetDay || !candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun weekdayToDayOfWeek(weekday: Int): DayOfWeek = when (weekday) {
        0 -> DayOfWeek.MONDAY
        1 -> DayOfWeek.TUESDAY
        2 -> DayOfWeek.WEDNESDAY
        3 -> DayOfWeek.THURSDAY
        4 -> DayOfWeek.FRIDAY
        5 -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }
}
