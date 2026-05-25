package com.homehub.dashboard.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(load())

    fun observe(): StateFlow<AppSettings> = state.asStateFlow()

    fun get(): AppSettings = state.value

    fun update(mutator: (AppSettings) -> AppSettings) {
        val updated = mutator(state.value)
        prefs.edit()
            .putString(KEY_LOCATION_NAME, updated.locationName)
            .putString(KEY_HUB_HOST, updated.hubHost)
            .putInt(KEY_IDLE_BRIGHTNESS, updated.idleBrightness)
            .putInt(KEY_ACTIVE_BRIGHTNESS, updated.activeBrightness)
            .putInt(KEY_IDLE_TIMEOUT_SECONDS, updated.idleTimeoutSeconds)
            .putInt(KEY_BRIGHTNESS_FADE_MILLIS, updated.brightnessFadeMillis)
            .putBoolean(KEY_USE_FULL_BLACK_IDLE, updated.useFullBlackIdle)
            .putInt(KEY_DEFAULT_ALARM_FADE_SECONDS, updated.defaultAlarmFadeSeconds)
            .putInt(KEY_DEFAULT_TARGET_VOLUME, updated.defaultTargetVolume)
            .putInt(KEY_DEFAULT_SNOOZE_MINUTES, updated.defaultSnoozeMinutes)
            .putString(KEY_DEFAULT_SOUND_URI, updated.defaultSoundUri)
            .putLong(KEY_LATEST_FORECAST_AT, updated.latestForecastAt)
            .putString(KEY_LATEST_FORECAST_JSON, updated.latestForecastJson)
            .putString(KEY_LATITUDE, updated.latitude.toString())
            .putString(KEY_LONGITUDE, updated.longitude.toString())
            .apply()
        state.value = updated
    }

    private fun load(): AppSettings {
        return AppSettings(
            locationName = prefs.getString(KEY_LOCATION_NAME, "Phu My, D7") ?: "Phu My, D7",
            latitude = prefs.getString(KEY_LATITUDE, "10.7326")?.toDoubleOrNull() ?: 10.7326,
            longitude = prefs.getString(KEY_LONGITUDE, "106.7219")?.toDoubleOrNull() ?: 106.7219,
            hubHost = prefs.getString(KEY_HUB_HOST, "100.126.58.18") ?: "100.126.58.18",
            idleBrightness = prefs.getInt(KEY_IDLE_BRIGHTNESS, 8),
            activeBrightness = prefs.getInt(KEY_ACTIVE_BRIGHTNESS, 80),
            idleTimeoutSeconds = prefs.getInt(KEY_IDLE_TIMEOUT_SECONDS, 20),
            brightnessFadeMillis = prefs.getInt(KEY_BRIGHTNESS_FADE_MILLIS, 1200),
            useFullBlackIdle = prefs.getBoolean(KEY_USE_FULL_BLACK_IDLE, false),
            defaultAlarmFadeSeconds = prefs.getInt(KEY_DEFAULT_ALARM_FADE_SECONDS, 120),
            defaultTargetVolume = prefs.getInt(KEY_DEFAULT_TARGET_VOLUME, 70),
            defaultSnoozeMinutes = prefs.getInt(KEY_DEFAULT_SNOOZE_MINUTES, 10),
            defaultSoundUri = prefs.getString(KEY_DEFAULT_SOUND_URI, null),
            latestForecastJson = prefs.getString(KEY_LATEST_FORECAST_JSON, null),
            latestForecastAt = prefs.getLong(KEY_LATEST_FORECAST_AT, 0L)
        )
    }

    companion object {
        private const val PREFS_NAME = "home_hub_settings"
        private const val KEY_LOCATION_NAME = "location_name"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_HUB_HOST = "hub_host"
        private const val KEY_IDLE_BRIGHTNESS = "idle_brightness"
        private const val KEY_ACTIVE_BRIGHTNESS = "active_brightness"
        private const val KEY_IDLE_TIMEOUT_SECONDS = "idle_timeout_seconds"
        private const val KEY_BRIGHTNESS_FADE_MILLIS = "brightness_fade_millis"
        private const val KEY_USE_FULL_BLACK_IDLE = "use_full_black_idle"
        private const val KEY_DEFAULT_ALARM_FADE_SECONDS = "default_alarm_fade_seconds"
        private const val KEY_DEFAULT_TARGET_VOLUME = "default_target_volume"
        private const val KEY_DEFAULT_SNOOZE_MINUTES = "default_snooze_minutes"
        private const val KEY_DEFAULT_SOUND_URI = "default_sound_uri"
        private const val KEY_LATEST_FORECAST_JSON = "latest_forecast_json"
        private const val KEY_LATEST_FORECAST_AT = "latest_forecast_at"
    }
}
