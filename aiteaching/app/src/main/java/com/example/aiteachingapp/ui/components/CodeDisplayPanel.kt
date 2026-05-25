package com.example.aiteachingapp.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiteachingapp.data.models.BilingualText
import com.example.aiteachingapp.data.models.CodeSnippet
import com.example.aiteachingapp.ui.AppLanguage
import com.example.aiteachingapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CodeDisplayPanel(
    snippets: List<CodeSnippet>,
    selectedSnippetIndex: Int,
    currentLineIndex: Int,
    language: AppLanguage,
    onSelectSnippet: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            scope.launch { listState.animateScrollToItem(currentLineIndex.coerceAtLeast(0)) }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CodeBackground)) {
        // File tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D30))
                .horizontalScroll(rememberScrollState())
        ) {
            if (snippets.isEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("code", fontSize = 12.sp, color = Color(0xFF9D9D9D), fontFamily = FontFamily.Monospace)
                }
            } else {
                snippets.forEachIndexed { index, snippet ->
                    val isSelected = index == selectedSnippetIndex
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) CodeBackground else Color(0xFF2D2D30))
                            .clickable { onSelectSnippet(index) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = snippet.fileName,
                            fontSize = 11.sp,
                            color = if (isSelected) Color(0xFFD4D4D4) else Color(0xFF9D9D9D),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        val currentSnippet = snippets.getOrNull(selectedSnippetIndex)
        val showEmpty = currentSnippet == null || currentLineIndex < 0

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (showEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (language == AppLanguage.EN) "Send the prompt to see code appear..."
                            else "Gửi prompt để xem code xuất hiện...",
                            color = Color(0xFF6B7280),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            COLOR_LEGEND.take(4).forEach { entry ->
                                ColorLegendChip(color = entry.first, label = entry.second)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            COLOR_LEGEND.drop(4).forEach { entry ->
                                ColorLegendChip(color = entry.first, label = entry.second)
                            }
                        }
                    }
                }
            } else {
                val visibleLines = currentSnippet!!.codeLines.take(currentLineIndex + 1)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(top = 4.dp)
                ) {
                    itemsIndexed(visibleLines) { lineIndex, codeLine ->
                        val isNewest = lineIndex == currentLineIndex
                        val bilingual = currentSnippet.lineExplanations[(lineIndex + 1).toString()]
                        val explanation = bilingual?.let {
                            if (language == AppLanguage.EN) it.en else it.vn
                        }
                        CodeLineRow(
                            lineNumber = lineIndex + 1,
                            code = codeLine,
                            explanation = explanation,
                            isHighlighted = isNewest
                        )
                    }
                }
            }
        }

        // Colour legend strip at bottom (always shown)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D30))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            COLOR_LEGEND.forEach { entry ->
                ColorLegendChip(color = entry.first, label = entry.second)
            }
        }
    }
}

@Composable
private fun ColorLegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(3.dp))
        Text(label, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CodeLineRow(
    lineNumber: Int,
    code: String,
    explanation: String?,
    isHighlighted: Boolean
) {
    var showExplanation by remember(lineNumber) { mutableStateOf(false) }
    LaunchedEffect(isHighlighted, explanation) {
        if (explanation != null) { delay(300); showExplanation = true }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHighlighted) NewLineHighlight else Color.Transparent)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Text(
                text = lineNumber.toString().padStart(3),
                color = CodeLineNumber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.width(36.dp).padding(start = 6.dp)
            )
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = buildSyntaxHighlighted(code),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
        AnimatedVisibility(visible = showExplanation && explanation != null, enter = fadeIn() + expandVertically()) {
            if (explanation != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp, end = 8.dp, bottom = 3.dp)
                        .background(ExplanationBackground, RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(12.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(explanation, color = ExplanationText, fontSize = 10.sp, fontStyle = FontStyle.Italic, lineHeight = 14.sp)
                }
            }
        }
    }
}

// buildSyntaxHighlighted lives in SyntaxHighlight.kt and is shared with the speech bubble.
