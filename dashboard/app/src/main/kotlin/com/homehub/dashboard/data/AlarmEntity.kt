package com.homehub.dashboard.data

data class AlarmEntity(
    val id: Long = 0,
    val weekday: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean,
    val repeatWeekly: Boolean,
    val soundUri: String?,
    val fadeInSeconds: Int,
    val targetVolume: Int,
    val snoozeMinutes: Int
)
