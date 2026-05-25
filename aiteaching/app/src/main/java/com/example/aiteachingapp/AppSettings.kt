package com.example.aiteachingapp

import android.content.Context
import com.example.aiteachingapp.ui.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide singleton for settings that must persist across screens and app restarts.
 * Call [init] once (from any ViewModel or Activity) before using [language].
 */
object AppSettings {

    private var prefs: android.content.SharedPreferences? = null

    private val _language = MutableStateFlow(AppLanguage.VN)
    val language = _language.asStateFlow()

    /** Idempotent — safe to call from multiple ViewModels. */
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val saved = prefs!!.getString("language", "VN")
        _language.value = if (saved == "EN") AppLanguage.EN else AppLanguage.VN
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        prefs?.edit()?.putString("language", lang.name)?.apply()
    }

    fun toggle() {
        setLanguage(if (_language.value == AppLanguage.VN) AppLanguage.EN else AppLanguage.VN)
    }
}
