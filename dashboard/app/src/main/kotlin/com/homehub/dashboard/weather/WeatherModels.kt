package com.homehub.dashboard.weather

data class DailyForecast(
    val dayLabel: String,
    val weatherCode: Int,
    val tempMaxC: Double,
    val tempMinC: Double
)

data class WeatherUiState(
    val locationName: String,
    val stale: Boolean,
    val cards: List<DailyForecast>
)
