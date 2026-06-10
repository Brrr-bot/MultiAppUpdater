package com.example.aiteachingapp.data

import android.content.Context

/**
 * Stores the values the student types into a tutorial's editable fields
 * (e.g. their own Firebase database URL), so the value survives app restarts
 * and is reused on every later step that references it.
 *
 * Keyed per-tutorial so two projects never share each other's credentials.
 */
class UserInputRepository(context: Context) {

    private val prefs = context.getSharedPreferences("tutorial_user_inputs", Context.MODE_PRIVATE)

    private fun storeKey(tutorialId: String, key: String) = "$tutorialId::$key"

    /** Save one value. Newlines are stripped so it can't break code line-counts. */
    fun set(tutorialId: String, key: String, value: String) {
        val clean = value.replace("\n", "").replace("\r", "").trim()
        prefs.edit().putString(storeKey(tutorialId, key), clean).apply()
    }

    fun get(tutorialId: String, key: String): String =
        prefs.getString(storeKey(tutorialId, key), "") ?: ""

    /** All saved values for a tutorial as key -> value (only the ones we've saved). */
    fun getAll(tutorialId: String): Map<String, String> {
        val prefix = "$tutorialId::"
        return prefs.all
            .filterKeys { it.startsWith(prefix) }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(prefix) to it } }
            .toMap()
    }
}

/**
 * Replaces every `{{KEY}}` token in [text] with the student's saved value.
 * If a key has no value yet, it's replaced with an obvious placeholder so the
 * copied code clearly tells them to paste their value in first.
 */
fun applyUserInputs(text: String, inputs: Map<String, String>): String {
    if (!text.contains("{{")) return text
    val regex = Regex("""\{\{\s*([A-Za-z0-9_]+)\s*}}""")
    return regex.replace(text) { match ->
        val key = match.groupValues[1]
        val value = inputs[key]?.trim().orEmpty()
        if (value.isNotEmpty()) value
        else "PASTE_YOUR_${key}_ABOVE"
    }
}
