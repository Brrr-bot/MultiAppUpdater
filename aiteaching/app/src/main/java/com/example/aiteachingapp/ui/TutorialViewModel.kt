package com.example.aiteachingapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiteachingapp.data.TutorialRepository
import com.example.aiteachingapp.data.models.BilingualText
import com.example.aiteachingapp.data.models.ChatMessage
import com.example.aiteachingapp.data.models.CodeSnippet
import com.example.aiteachingapp.data.models.TutorialStep
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AppLanguage { EN, VN }

class TutorialViewModel(
    application: Application,
    private val tutorialId: String = ""
) : AndroidViewModel(application) {

    private val repository = TutorialRepository(application)
    private val progressRepository = com.example.aiteachingapp.data.ProgressRepository(application)
    private val userInputRepository = com.example.aiteachingapp.data.UserInputRepository(application)
    private val tutorialData = if (tutorialId.isBlank()) repository.loadTutorial()
                               else repository.loadByTutorialId(tutorialId)
    private val steps: List<TutorialStep> = tutorialData.steps

    val language = com.example.aiteachingapp.AppSettings.language

    private val _currentStepIndex = MutableStateFlow(0)

    private val _currentStep = MutableStateFlow(steps.first())
    val currentStep = _currentStep.asStateFlow()

    private val _totalSteps = MutableStateFlow(steps.size)
    val totalSteps = _totalSteps.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping = _isAiTyping.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText = _streamingText.asStateFlow()

    private val _currentCodeLineIndex = MutableStateFlow(-1)
    val currentCodeLineIndex = _currentCodeLineIndex.asStateFlow()

    private val _isStepComplete = MutableStateFlow(false)
    val isStepComplete = _isStepComplete.asStateFlow()

    private val _isAnimating = MutableStateFlow(false)
    val isAnimating = _isAnimating.asStateFlow()

    private val _selectedSnippetIndex = MutableStateFlow(0)
    val selectedSnippetIndex = _selectedSnippetIndex.asStateFlow()

    private val _showInterstitial = MutableStateFlow(false)
    val showInterstitial = _showInterstitial.asStateFlow()

    /** Student-entered values (e.g. their Firebase URL), key -> value. Persisted. */
    private val _userInputs = MutableStateFlow(
        userInputRepository.getAll(tutorialData.tutorialId)
    )
    val userInputs = _userInputs.asStateFlow()

    /** Save what the student typed into an editable field. Persists immediately,
     *  so it's already saved by the time they move to the next step. */
    fun setUserInput(key: String, value: String) {
        userInputRepository.set(tutorialData.tutorialId, key, value)
        _userInputs.value = _userInputs.value.toMutableMap().apply {
            put(key, value.replace("\n", "").replace("\r", "").trim())
        }
    }

    private val _awaitingChoice = MutableStateFlow(false)
    val awaitingChoice = _awaitingChoice.asStateFlow()

    /** True after AI finishes streaming on a no-choice step — waits for user to tap "Write Code". */
    private val _awaitingCodeWrite = MutableStateFlow(false)
    val awaitingCodeWrite = _awaitingCodeWrite.asStateFlow()

    /** Snippets merged across all steps 1..currentStepIndex. Files with the
     * same fileName are concatenated; line explanations are offset to match. */
    private val _cumulativeSnippets = MutableStateFlow<List<CodeSnippet>>(emptyList())
    val cumulativeSnippets = _cumulativeSnippets.asStateFlow()

    /** For each cumulative snippet, how many lines existed BEFORE the current
     *  step started (so animation can skip past them and only animate new lines). */
    private var previousLineCountBySnippetIndex: IntArray = IntArray(0)

    /** The code line index from which the CURRENT animation started.
     *  Exposed so TutorialScreen can derive animated card-weight progress. */
    private val _animationStartLineIndex = MutableStateFlow(0)
    val animationStartLineIndex = _animationStartLineIndex.asStateFlow()

    private var animationJob: Job? = null

    init {
        com.example.aiteachingapp.AppSettings.init(application)
        rebuildCumulativeForCurrentStep(initial = true)
    }

    fun BilingualText.current() = if (language.value == AppLanguage.EN) en else vn

    fun toggleLanguage() { com.example.aiteachingapp.AppSettings.toggle() }

    fun isEnglish() = language.value == AppLanguage.EN

    fun onSendMessage(message: String) {
        if (_isAnimating.value) return
        _isAnimating.value = true
        animationJob = viewModelScope.launch {
            _chatMessages.value = _chatMessages.value + ChatMessage(message, isUser = true)
            _isAiTyping.value = true
            delay(Random.nextLong(1500, 2500))
            _isAiTyping.value = false
            val response = _currentStep.value.aiResponse.current()
            streamResponse(response)
            delay(500)

            if (_currentStep.value.answerChoices.isNotEmpty()) {
                // Wait for the user to pick an answer before code animates.
                _awaitingChoice.value = true
                _isAnimating.value = false
            } else {
                // Wait for the user to tap "Write Code" before animating.
                _awaitingCodeWrite.value = true
                _isAnimating.value = false
            }
        }
    }

    /** Student tapped "Write Code" — start the code animation now. */
    fun startCodeWrite() {
        if (_isAnimating.value) return
        _awaitingCodeWrite.value = false
        _isAnimating.value = true
        animationJob = viewModelScope.launch {
            animateCodeLines()
            _isStepComplete.value = true
            _isAnimating.value = false
        }
    }

    /** Student tapped one of the answer buttons. */
    fun pickAnswerChoice(choiceText: String) {
        _chatMessages.value = _chatMessages.value + ChatMessage(choiceText, isUser = true)
        _awaitingChoice.value = false
        // Now the AI "starts writing" — animate the code in.
        if (_isAnimating.value) return
        _isAnimating.value = true
        animationJob = viewModelScope.launch {
            // Brief beat so the chat user-message can settle in first
            delay(400)
            animateCodeLines()
            _isStepComplete.value = true
            _isAnimating.value = false
        }
    }

    private suspend fun streamResponse(text: String) {
        val words = text.split(" ")
        _streamingText.value = ""
        words.forEachIndexed { index, word ->
            _streamingText.value += if (index == 0) word else " $word"
            val delayMs = when {
                word.endsWith(".") || word.endsWith("!") || word.endsWith("?") -> 300L
                word.endsWith(",") || word.endsWith(";") || word.endsWith(":") -> 150L
                else -> Random.nextLong(40, 85)
            }
            delay(delayMs)
        }
        delay(350)
        _chatMessages.value = _chatMessages.value + ChatMessage(_streamingText.value, isUser = false)
        _streamingText.value = ""
    }

    private suspend fun animateCodeLines() {
        val snippet = _cumulativeSnippets.value.getOrNull(_selectedSnippetIndex.value) ?: return
        val startLine = previousLineCountBySnippetIndex
            .getOrElse(_selectedSnippetIndex.value) { 0 }
        // Publish the start line so TutorialScreen can compute card-weight progress.
        _animationStartLineIndex.value = startLine
        // Show all previous-step lines instantly; animate only the new ones.
        _currentCodeLineIndex.value = (startLine - 1).coerceAtLeast(-1)
        for (lineIndex in startLine until snippet.codeLines.size) {
            _currentCodeLineIndex.value = lineIndex
            delay(160)
        }
    }

    /**
     * Walks every step 0..currentStepIndex, groups snippets by fileName, and
     * concatenates the code (offsetting per-line explanations). Tracks how
     * many lines existed BEFORE the current step so animation can resume.
     */
    private fun rebuildCumulativeForCurrentStep(initial: Boolean = false) {
        val current = _currentStepIndex.value

        // fileName -> (accumulatedCode, accumulatedExplanations, linesBeforeCurrentStep, addedInCurrent)
        data class Accum(
            var code: String = "",
            var explanations: MutableMap<String, BilingualText> = mutableMapOf(),
            var linesBeforeCurrent: Int = 0,
            var addedInCurrent: Boolean = false
        )

        val perFile = linkedMapOf<String, Accum>()

        for (stepIdx in 0..current) {
            val step = steps[stepIdx]
            for (snip in step.codeSnippets) {
                val acc = perFile.getOrPut(snip.fileName) { Accum() }
                if (stepIdx == current) {
                    // Freeze the line-count snapshot just before adding current-step content
                    if (!acc.addedInCurrent) {
                        acc.linesBeforeCurrent =
                            if (acc.code.isEmpty()) 0 else acc.code.split("\n").size
                        acc.addedInCurrent = true
                    }
                }
                val priorLineCount = if (acc.code.isEmpty()) 0 else acc.code.split("\n").size
                val offsetExplanations = snip.lineExplanations.mapKeys { (k, _) ->
                    val asInt = k.toIntOrNull() ?: return@mapKeys k
                    (asInt + priorLineCount).toString()
                }
                acc.explanations.putAll(offsetExplanations)
                acc.code = if (acc.code.isEmpty()) snip.code else "${acc.code}\n${snip.code}"
            }
        }

        val snippets = perFile.map { (name, acc) ->
            CodeSnippet(
                fileName = name,
                code = acc.code,
                lineExplanations = acc.explanations
            )
        }
        _cumulativeSnippets.value = snippets
        previousLineCountBySnippetIndex = perFile.values.map {
            if (it.addedInCurrent) it.linesBeforeCurrent else it.code.split("\n").size
        }.toIntArray()

        // Pre-select the last snippet that was added/extended in the current step
        val defaultIdx = perFile.values.indexOfLast { it.addedInCurrent }.coerceAtLeast(0)
        _selectedSnippetIndex.value = defaultIdx
        // Show prior lines immediately; animation will fire on send and add new ones.
        val startLine = previousLineCountBySnippetIndex.getOrElse(defaultIdx) { 0 }
        _currentCodeLineIndex.value = (startLine - 1).coerceAtLeast(-1)
        // CRITICAL: keep animationStartLineIndex in sync with startLine so that
        // codeActuallyAnimating stays false until animateCodeLines() actually runs.
        // Without this, setting isAnimating=true in onSendMessage would make
        // currentLineIndex (startLine-1) >= animationStartLineIndex (0) = true,
        // falsely triggering CODE_WRITING phase for the entire AI response duration.
        _animationStartLineIndex.value = startLine
        // If THIS step adds nothing to the selected snippet, show its full body.
        if (!initial && snippets.getOrNull(defaultIdx)?.let { snip ->
                startLine >= snip.codeLines.size
            } == true) {
            _currentCodeLineIndex.value = snippets[defaultIdx].codeLines.size - 1
        }
    }

    /** Called when the user taps "Next" — shows Clawd's interstitial first (if one exists). */
    fun requestNextStep() {
        val explanation = _currentStep.value.clawdExplanation.current()
        val nextIndex = _currentStepIndex.value + 1
        val hasNext = nextIndex < steps.size
        // Skip interstitial on the very last step or when no explanation written yet
        if (explanation.isBlank() || !hasNext) {
            advanceToNextStep()
        } else {
            _showInterstitial.value = true
        }
    }

    /** Dismiss the interstitial and actually move to the next step. */
    fun dismissInterstitialAndAdvance() {
        _showInterstitial.value = false
        advanceToNextStep()
    }

    /** Jump to a specific step index — called when opening a saved project from the menu. */
    fun startAt(stepIndex: Int) {
        val clamped = stepIndex.coerceIn(0, steps.size - 1)
        if (_currentStepIndex.value == clamped) return
        _currentStepIndex.value = clamped
        _currentStep.value = steps[clamped]
        resetStepState()
        rebuildCumulativeForCurrentStep()
    }

    private fun advanceToNextStep() {
        val nextIndex = _currentStepIndex.value + 1
        if (nextIndex < steps.size) {
            _currentStepIndex.value = nextIndex
            _currentStep.value = steps[nextIndex]
            // Save highest progress reached
            progressRepository.saveProgress(tutorialData.tutorialId, nextIndex)
            resetStepState()
            rebuildCumulativeForCurrentStep()
        }
    }

    /** Resets the current step to its initial state (used when Back is pressed on step 1). */
    fun resetCurrentStep() {
        resetStepState()
        rebuildCumulativeForCurrentStep()
    }

    /** Backwards-compat shim — call sites that bypass the interstitial use this. */
    fun goToNextStep() = advanceToNextStep()

    fun goToPreviousStep() {
        val prevIndex = _currentStepIndex.value - 1
        if (prevIndex >= 0) {
            _currentStepIndex.value = prevIndex
            _currentStep.value = steps[prevIndex]
            resetStepState()
            rebuildCumulativeForCurrentStep()
        }
    }

    private fun resetStepState() {
        _chatMessages.value = emptyList()
        _streamingText.value = ""
        _currentCodeLineIndex.value = -1
        _isStepComplete.value = false
        _isAnimating.value = false
        _selectedSnippetIndex.value = 0
        _awaitingChoice.value = false
        _awaitingCodeWrite.value = false
        _animationStartLineIndex.value = 0
        animationJob?.cancel()
    }

    fun selectSnippet(index: Int) {
        _selectedSnippetIndex.value = index
        val snippet = _cumulativeSnippets.value.getOrNull(index)
        // For previously-completed files, show all lines instantly.
        // For the active step's file, restore the "old lines shown, new not yet" state.
        val startLine = previousLineCountBySnippetIndex.getOrElse(index) { 0 }
        _currentCodeLineIndex.value = when {
            snippet == null -> -1
            startLine >= snippet.codeLines.size -> snippet.codeLines.size - 1
            else -> (startLine - 1).coerceAtLeast(-1)
        }
    }

    fun getTutorialTitle(): String {
        return if (language.value == AppLanguage.EN) tutorialData.title.en else tutorialData.title.vn
    }
}
