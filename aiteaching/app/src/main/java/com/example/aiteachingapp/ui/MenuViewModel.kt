package com.example.aiteachingapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.aiteachingapp.data.ProgressRepository
import com.example.aiteachingapp.data.TutorialRepository
import com.example.aiteachingapp.data.models.TutorialData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MenuViewModel(application: Application) : AndroidViewModel(application) {

    private val tutorialRepo  = TutorialRepository(application)
    private val progressRepo  = ProgressRepository(application)

    data class ProjectItem(
        val tutorial: TutorialData,
        /** 0-based index of the last saved step. */
        val savedStepIndex: Int,
        val totalSteps: Int
    ) {
        val isNotStarted: Boolean get() = savedStepIndex == 0
        val isComplete: Boolean   get() = savedStepIndex >= totalSteps - 1
        val isInProgress: Boolean get() = !isNotStarted && !isComplete
        /** 1-based step number shown to the user. */
        val displayStep: Int get() = savedStepIndex + 1
        val progressFraction: Float get() = if (totalSteps <= 1) 0f
            else savedStepIndex.toFloat() / (totalSteps - 1).toFloat()
    }

    private val _projects = MutableStateFlow<List<ProjectItem>>(emptyList())
    val projects = _projects.asStateFlow()

    val language = com.example.aiteachingapp.AppSettings.language

    init {
        com.example.aiteachingapp.AppSettings.init(application)
        refresh()
    }

    fun refresh() {
        val tutorials = tutorialRepo.listAllTutorials()
        _projects.value = tutorials.map { t ->
            val saved = progressRepo.loadProgress(t.tutorialId)
            ProjectItem(t, saved, t.steps.size)
        }
    }

    fun resetProgress(tutorialId: String) {
        progressRepo.resetProgress(tutorialId)
        refresh()
    }

    fun toggleLanguage() { com.example.aiteachingapp.AppSettings.toggle() }
}
