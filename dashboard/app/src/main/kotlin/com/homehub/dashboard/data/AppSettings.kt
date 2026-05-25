package com.homehub.dashboard.data

data class AppSettings(
    val locationName: String = "Phu My, D7",
    val latitude: Double = 10.7326,
    val longitude: Double = 106.7219,
    val hubHost: String = "100.126.58.18",
    val idleBrightness: Int = 8,
    val activeBrightness: Int = 80,
    val idleTimeoutSeconds: Int = 20,
    val brightnessFadeMillis: Int = 1200,
    val useFullBlackIdle: Boolean = false,
    val defaultAlarmFadeSeconds: Int = 120,
    val defaultTargetVolume: Int = 70,
    val defaultSnoozeMinutes: Int = 10,
    val defaultSoundUri: String? = null,
    val latestForecastJson: String? = null,
    val latestForecastAt: Long = 0L
)
