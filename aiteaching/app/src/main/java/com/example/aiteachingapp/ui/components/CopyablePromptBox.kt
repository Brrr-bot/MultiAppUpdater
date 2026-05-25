package com.example.aiteachingapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiteachingapp.ui.theme.ClaudeOrange
import com.example.aiteachingapp.ui.theme.DarkBugSurface
import com.example.aiteachingapp.ui.theme.DarkSurface2
import com.example.aiteachingapp.ui.theme.DarkSurface3
import com.example.aiteachingapp.ui.theme.DarkText
import com.example.aiteachingapp.ui.theme.DarkTextSecondary
import com.example.aiteachingapp.ui.theme.DarkDivider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CopyablePromptBox(
    promptText: String,
    rationale: String = "",
    label: String = "Prompt cho AI",
    isBug: Boolean = false,
    onCopied: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    val borderColor = if (isBug) Color(0xFF7F1D1D) else DarkDivider
    val labelColor  = if (isBug) Color(0xFFFCA5A5) else DarkTextSecondary
    val btnColor    = if (isBug) Color(0xFFDC2626) else ClaudeOrange
    val bgColor     = if (isBug) DarkBugSurface else DarkSurface2
    val textColor   = if (isBug) Color(0xFFFCA5A5) else DarkText

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Rationale — explains WHY this prompt, not copied
            if (rationale.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(DarkSurface3, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = rationale,
                        fontSize = 10.sp,
                        color = DarkTextSecondary,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 14.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor
                )
                Button(
                    onClick = {
                        // Copies only the prompt text, not the rationale
                        clipboardManager.setText(AnnotatedString(promptText))
                        onCopied()
                        scope.launch {
                            copied = true
                            delay(2000)
                            copied = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    AnimatedContent(targetState = copied, label = "copy") { isCopied ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCopied) {
                                Icon(Icons.Default.Done, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(text = if (isCopied) "Copied!" else "Copy", fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 110.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = promptText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = textColor,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
