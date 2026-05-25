package com.example.aiteachingapp.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiteachingapp.ui.AppLanguage

@Composable
fun CompletionScreen(
    language: AppLanguage,
    onInstallApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEn = language == AppLanguage.EN
    var showMockApp by remember { mutableStateOf(false) }

    if (showMockApp) {
        MockRainAlertApp(isEn = isEn, onClose = { showMockApp = false })
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10B981), Color(0xFF059669))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🏆", fontSize = 40.sp)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isEn) "Congratulations!" else "Chúc mừng!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isEn) "You've built Rain Alert Vietnam!" else "Bạn đã xây dựng Rain Alert Vietnam!",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        // Summary card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isEn) "What you built:" else "Những gì bạn đã xây dựng:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1F2937)
                )
                Spacer(Modifier.height(10.dp))
                val features = if (isEn) listOf(
                    "Real-time GPS location detection",
                    "Rain reporting with Firebase database",
                    "Push notifications via Firebase Cloud Messaging",
                    "Interactive Google Maps with rain markers",
                    "Community rain alerts for Ho Chi Minh City",
                    "Fixed 3 real bugs like a professional developer"
                ) else listOf(
                    "Phát hiện vị trí GPS theo thời gian thực",
                    "Báo cáo mưa với Firebase Realtime Database",
                    "Thông báo đẩy qua Firebase Cloud Messaging",
                    "Bản đồ Google Maps với marker mưa theo màu",
                    "Cảnh báo mưa cộng đồng cho TP.HCM",
                    "Sửa 3 bug thực tế như lập trình viên chuyên nghiệp"
                )
                features.forEach { feature ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("✓ ", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(feature, fontSize = 13.sp, color = Color(0xFF374151))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { showMockApp = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isEn) "Preview Rain Alert App" else "Xem thử Rain Alert App",
                color = Color(0xFF10B981),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onInstallApp,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text("📱", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isEn) "Install Rain Alert on Device" else "Cài Rain Alert lên điện thoại",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // Next steps card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isEn) "Next Steps" else "Bước tiếp theo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                val nextSteps = if (isEn) listOf(
                    "Add user authentication with Firebase Auth",
                    "Publish to Google Play Store",
                    "Build the iOS version with Swift",
                    "Add weather API integration for forecasts"
                ) else listOf(
                    "Thêm đăng nhập với Firebase Auth",
                    "Xuất bản lên Google Play Store",
                    "Xây dựng phiên bản iOS với Swift",
                    "Tích hợp API thời tiết để dự báo mưa"
                )
                nextSteps.forEach { step ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("→ ", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(step, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MockRainAlertApp(isEn: Boolean, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Mock TopBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1565C0))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("Rain Alert Vietnam", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Mock weather card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("☀️", fontSize = 40.sp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Quận 1, TP.HCM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(if (isEn) "Sunny • 32°C" else "Trời nắng • 32°C", fontSize = 14.sp, color = Color(0xFF546E7A))
                        Text(if (isEn) "Updated 2 minutes ago" else "Cập nhật 2 phút trước", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Mock map placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", fontSize = 36.sp)
                    Text(if (isEn) "Rain Map – Ho Chi Minh City" else "Bản đồ mưa – TP. Hồ Chí Minh",
                        fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RainLegend("🔵", if (isEn) "Light" else "Nhẹ")
                        RainLegend("🟦", if (isEn) "Medium" else "Vừa")
                        RainLegend("🔴", if (isEn) "Heavy" else "To")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Mock recent reports
            Text(if (isEn) "Recent Rain Reports" else "Báo cáo mưa gần đây",
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1F2937))
            Spacer(Modifier.height(8.dp))
            listOf(
                Triple("Quận 7", if (isEn) "Heavy rain" else "Mưa lớn", "5 phút"),
                Triple("Quận 4", if (isEn) "Light rain" else "Mưa nhẹ", "12 phút"),
                Triple("Bình Thạnh", if (isEn) "Medium rain" else "Mưa vừa", "28 phút")
            ).forEach { (district, intensity, time) ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFF)),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌧️", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(district, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(intensity, fontSize = 12.sp, color = Color(0xFF546E7A))
                        }
                        Text("$time ${if (isEn) "ago" else "trước"}", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                    }
                }
            }

            Spacer(Modifier.height(70.dp))
        }

        // Mock FAB
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text("🌧️ ${if (isEn) "Report Rain" else "Báo cáo mưa"}", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RainLegend(emoji: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 12.sp)
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF4CAF50))
    }
}
