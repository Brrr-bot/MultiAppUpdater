package com.homehub.dashboard.weather

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.homehub.dashboard.data.AppSettingsRepository
import com.homehub.dashboard.util.RemoteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class WeatherRepository(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val settingsRepository: AppSettingsRepository
) {
    suspend fun fetch(): WeatherUiState = withContext(Dispatchers.IO) {
        val settings = settingsRepository.get()
        val request = Request.Builder()
            .url(
                "https://api.open-meteo.com/v1/forecast?latitude=${settings.latitude}" +
                    "&longitude=${settings.longitude}&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&forecast_days=7&timezone=auto"
            )
            .build()
        try {
            RemoteLogger.i("weather fetch → ${settings.latitude},${settings.longitude}")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Weather status ${response.code}")
                val body = response.body?.string().orEmpty()
                RemoteLogger.i("weather ok, body length=${body.length}")
                settingsRepository.update { it.copy(latestForecastJson = body, latestForecastAt = System.currentTimeMillis()) }
                parse(body, settings.locationName, false)
            }
        } catch (e: Throwable) {
            RemoteLogger.e("weather fetch failed", e)
            val cached = settings.latestForecastJson
            if (cached != null) parse(cached, settings.locationName, true)
            else throw e
        }
    }

    private fun parse(json: String, locationName: String, stale: Boolean): WeatherUiState {
        val root = gson.fromJson(json, JsonObject::class.java)
        val daily = root.getAsJsonObject("daily")
        val times = daily.getAsJsonArray("time")
        // API uses "weather_code" (renamed from "weathercode" in 2024)
        val codes = daily.getAsJsonArray("weather_code")
            ?: daily.getAsJsonArray("weathercode")
        val max = daily.getAsJsonArray("temperature_2m_max")
        val min = daily.getAsJsonArray("temperature_2m_min")
        val cards = buildList {
            for (index in 0 until minOf(7, times.size())) {
                val date = LocalDate.parse(times[index].asString)
                add(
                    DailyForecast(
                        dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        weatherCode = codes[index].asInt,
                        tempMaxC = max[index].asDouble,
                        tempMinC = min[index].asDouble
                    )
                )
            }
        }
        RemoteLogger.i("weather parsed ${cards.size} days: ${cards.firstOrNull()?.let { "${it.dayLabel} ${it.tempMaxC}/${it.tempMinC}" }}")
        return WeatherUiState(locationName, stale, cards)
    }
}
