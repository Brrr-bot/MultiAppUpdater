package com.example.aiteachingapp.data

import android.content.Context
import com.example.aiteachingapp.data.models.TutorialData
import com.google.gson.Gson

class TutorialRepository(private val context: Context) {

    private val cache = mutableMapOf<String, TutorialData>()
    private val gson = Gson()

    /** Load the default tutorial (rain_alert). */
    fun loadTutorial(): TutorialData = loadFromAsset("rain_alert_tutorial.json")

    /** Find and load a tutorial by its tutorialId field. */
    fun loadByTutorialId(tutorialId: String): TutorialData {
        val files = context.assets.list("") ?: return loadTutorial()
        val filename = files.firstOrNull { it.endsWith("_tutorial.json") &&
            runCatching { loadFromAsset(it).tutorialId == tutorialId }.getOrDefault(false) }
        return if (filename != null) loadFromAsset(filename) else loadTutorial()
    }

    /** Load any tutorial by asset filename. */
    fun loadFromAsset(filename: String): TutorialData {
        return cache.getOrPut(filename) {
            val json = context.assets.open(filename).bufferedReader().use { it.readText() }
            gson.fromJson(json, TutorialData::class.java)
        }
    }

    /** Return all tutorial JSON files found in assets. */
    fun listAllTutorials(): List<TutorialData> {
        val files = context.assets.list("") ?: return emptyList()
        return files
            .filter { it.endsWith("_tutorial.json") }
            .mapNotNull { filename ->
                runCatching { loadFromAsset(filename) }.getOrNull()
            }
            .sortedWith(compareBy({ it.order }, { it.title.en }))
    }
}
