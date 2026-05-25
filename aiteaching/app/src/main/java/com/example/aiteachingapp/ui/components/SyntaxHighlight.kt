package com.example.aiteachingapp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.aiteachingapp.ui.theme.*

internal val KEYWORDS = setOf(
    "class", "fun", "val", "var", "if", "else", "when", "return", "import",
    "package", "object", "override", "private", "public", "data", "enum",
    "companion", "suspend", "null", "true", "false", "for", "while", "do",
    "try", "catch", "finally", "throw", "is", "as", "in", "by", "this",
    "super", "interface", "abstract", "open", "const", "inline", "reified",
    "it", "let", "also", "apply", "run", "with", "sealed", "init", "lazy"
)

internal val COLOR_LEGEND: List<Pair<Color, String>> = listOf(
    Pair(CodeKeyword,    "val/fun/class"),
    Pair(CodeString,     "\"strings\""),
    Pair(CodeNumber,     "numbers"),
    Pair(CodeAnnotation, "@annotations"),
    Pair(CodeFunction,   "functions()"),
    Pair(CodeClass,      "ClassName"),
    Pair(CodeComment,    "// comments"),
)

/**
 * Tokenises a string and colours Kotlin-style tokens (keywords, strings, numbers,
 * @annotations, ClassNames, functionCalls(), // comments, brackets).
 *
 * Also strips backticks so prose like "We used `val` to make a variable" can be
 * marked up by the author and rendered with the inline word highlighted.
 */
fun buildSyntaxHighlighted(code: String): AnnotatedString = buildAnnotatedString {
    // Whole-line comment
    val trimmed = code.trimStart()
    if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
        withStyle(SpanStyle(color = CodeComment)) { append(code) }
        return@buildAnnotatedString
    }

    var i = 0
    while (i < code.length) {
        val c = code[i]

        // Backticks are author markers — skip them silently.
        if (c == '`') { i++; continue }

        // Annotation: @Word
        if (c == '@' && i + 1 < code.length && code[i + 1].isLetter()) {
            var j = i + 1
            while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            withStyle(SpanStyle(color = CodeAnnotation)) { append(code.substring(i, j)) }
            i = j; continue
        }

        // String literal: "..."
        if (c == '"') {
            val end = code.indexOf('"', i + 1).takeIf { it >= 0 } ?: (code.length - 1)
            withStyle(SpanStyle(color = CodeString)) { append(code.substring(i, end + 1)) }
            i = end + 1; continue
        }

        // Number literal: starts with digit
        if (c.isDigit()) {
            var j = i + 1
            while (j < code.length && (code[j].isDigit() || code[j] == '.' || code[j] == 'L' || code[j] == 'f')) j++
            withStyle(SpanStyle(color = CodeNumber)) { append(code.substring(i, j)) }
            i = j; continue
        }

        // Word token: keyword / class / function / plain identifier
        if (c.isLetter() || c == '_') {
            var j = i + 1
            while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            val word = code.substring(i, j)
            var k = j
            while (k < code.length && code[k] == ' ') k++
            val followedByParen = k < code.length && code[k] == '('

            val color = when {
                word in KEYWORDS            -> CodeKeyword
                followedByParen             -> CodeFunction
                word[0].isUpperCase()       -> CodeClass
                else                        -> DarkText  // prose-friendly default
            }
            withStyle(SpanStyle(color = color)) { append(word) }
            i = j; continue
        }

        // Brackets and braces
        if (c in "(){}[]") {
            withStyle(SpanStyle(color = Color(0xFFFFD700))) { append(c) }
            i++; continue
        }

        withStyle(SpanStyle(color = DarkText)) { append(c) }
        i++
    }
}
