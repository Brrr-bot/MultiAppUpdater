package com.example.aiteachingapp.data.models

data class BilingualText(val en: String = "", val vn: String = "")

data class TutorialData(
    val tutorialId: String = "",
    val title: BilingualText = BilingualText(),
    val steps: List<TutorialStep> = emptyList()
)

/** A colour-coded vocabulary term shown on the between-step interstitial. */
data class VocabTerm(
    val term: String = "",
    /** Hex colour string, e.g. "#60A5FA". Determines the chip colour. */
    val color: String = "#DA7756",
    val explanation: BilingualText = BilingualText()
)

data class TutorialStep(
    val stepNumber: Int = 0,
    val type: String = "lesson",   // "lesson", "bug", "final"
    val title: BilingualText = BilingualText(),
    val instruction: BilingualText = BilingualText(),
    val promptText: BilingualText = BilingualText(),
    val promptRationale: BilingualText = BilingualText(),
    val aiResponse: BilingualText = BilingualText(),
    val codeSnippets: List<CodeSnippet> = emptyList(),
    val bugNumber: Int = 0,
    // Clawd's between-steps interstitial: what we just did + what's next.
    // Use `code` token wrapping `like this` so the syntax highlighter colours it.
    val clawdExplanation: BilingualText = BilingualText(),
    // When the AI's response ends with a question, present these as tap-buttons.
    // We only list the answer(s) we want the student to choose (the path forward).
    val answerChoices: List<BilingualText> = emptyList(),
    // Key terms from this step's code, colour-coded by category.
    val vocabTerms: List<VocabTerm> = emptyList(),
    // Plain-English explanation of exactly what the code in this step does.
    val codeExplainer: BilingualText = BilingualText()
)

data class CodeSnippet(
    val fileName: String = "",
    val code: String = "",
    val lineExplanations: Map<String, BilingualText> = emptyMap()
) {
    val codeLines: List<String> get() = code.split("\n")
}

data class ChatMessage(val text: String, val isUser: Boolean)
