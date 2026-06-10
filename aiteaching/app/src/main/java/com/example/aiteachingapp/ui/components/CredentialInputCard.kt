package com.example.aiteachingapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiteachingapp.data.models.UserInputField
import com.example.aiteachingapp.ui.theme.ClaudeOrange
import com.example.aiteachingapp.ui.theme.DarkSurface2
import com.example.aiteachingapp.ui.theme.DarkSurface3
import com.example.aiteachingapp.ui.theme.DarkText
import com.example.aiteachingapp.ui.theme.DarkTextSecondary

/**
 * A bright, hard-to-miss card where the student pastes their OWN value
 * (e.g. their Firebase database URL). What they type is saved straight away
 * and injected into the code/prompt they copy — here and on every later step.
 */
@Composable
fun CredentialInputCard(
    fields: List<UserInputField>,
    values: Map<String, String>,
    isEnglish: Boolean,
    onValueChange: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (fields.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface2),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, ClaudeOrange, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Text(
                text = if (isEnglish) "📋  PASTE YOUR OWN INFO HERE"
                       else "📋  DÁN THÔNG TIN CỦA BẠN VÀO ĐÂY",
                color = ClaudeOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = if (isEnglish)
                    "We save this and put it into your code automatically — you only type it once."
                else
                    "Chúng tôi lưu lại và tự động đưa vào code của bạn — bạn chỉ cần gõ một lần.",
                color = DarkTextSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            fields.forEach { field ->
                val value = values[field.key].orEmpty()
                val saved = value.trim().isNotEmpty()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEnglish) field.label.en else field.label.vn,
                        color = DarkText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (saved) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (isEnglish) "Saved" else "Đã lưu",
                            color = Color(0xFF22C55E),
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { onValueChange(field.key, it) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = DarkText
                    ),
                    placeholder = {
                        Text(
                            field.example.ifBlank { "paste here…" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClaudeOrange,
                        unfocusedBorderColor = DarkSurface3,
                        focusedContainerColor = DarkSurface3,
                        unfocusedContainerColor = DarkSurface3,
                        cursorColor = ClaudeOrange
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                val help = if (isEnglish) field.help.en else field.help.vn
                if (help.isNotBlank()) {
                    Text(
                        text = help,
                        color = DarkTextSecondary,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
