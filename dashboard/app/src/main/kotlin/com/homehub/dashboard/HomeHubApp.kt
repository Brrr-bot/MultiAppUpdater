package com.homehub.dashboard

import android.app.Application
import com.google.gson.Gson
import com.homehub.dashboard.alarm.AlarmScheduler
import com.homehub.dashboard.data.AlarmRepository
import com.homehub.dashboard.data.AppSettingsRepository
import com.homehub.dashboard.hub.HubRepository
import com.homehub.dashboard.weather.WeatherRepository
import okhttp3.OkHttpClient

class HomeHubApp : Application() {
    lateinit var alarmRepository: AlarmRepository
        private set
    lateinit var settingsRepository: AppSettingsRepository
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set
    lateinit var hubRepository: HubRepository
        private set
    lateinit var weatherRepository: WeatherRepository
        private set

    override fun onCreate() {
        super.onCreate()
        alarmRepository = AlarmRepository(applicationContext)
        settingsRepository = AppSettingsRepository(applicationContext)
        alarmScheduler = AlarmScheduler(applicationContext, alarmRepository)
        val okHttpClient = OkHttpClient()
        val gson = Gson()
        hubRepository = HubRepository(okHttpClient, gson, settingsRepository)
        weatherRepository = WeatherRepository(okHttpClient, gson, settingsRepository)
    }
}
