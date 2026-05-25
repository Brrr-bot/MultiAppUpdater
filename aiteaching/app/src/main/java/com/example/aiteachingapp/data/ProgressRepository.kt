package com.example.aiteachingapp.data

import android.content.Context

/**
 * Persists the highest step index reached for each tutorial across app restarts.
 * Progress only moves forward — going back doesn't erase it.
 */
class ProgressRepository(context: Context) {

    private val prefs = context.getSharedPreferences("tutorial_progress", Context.MODE_PRIVATE)

    /** Save progress — only advances, never goes backwards. */
    fun saveProgress(tutorialId: String, stepIndex: Int) {
        val current = prefs.getInt(tutorialId, 0)
        if (stepIndex > current) prefs.edit().putInt(tutorialId, stepIndex).apply()
    }

    /** Load last saved step index (0-based). Returns 0 if never started. */
    fun loadProgress(tutorialId: String): Int = prefs.getInt(tutorialId, 0)

    /** True if the user has started but not completed this tutorial. */
    fun isInProgress(tutorialId: String, totalSteps: Int): Boolean {
        val idx = loadProgress(tutorialId)
        return idx > 0 && idx < totalSteps - 1
    }

    /** True if the user has reached the final step. */
    fun isComplete(tutorialId: String, totalSteps: Int): Boolean =
        loadProgress(tutorialId) >= totalSteps - 1

    /** Reset progress for a tutorial back to step 0. */
    fun resetProgress(tutorialId: String) {
        prefs.edit().remove(tutorialId).apply()
    }
}
