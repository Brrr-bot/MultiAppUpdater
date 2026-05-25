package com.example.aiteachingapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.aiteachingapp.R
import com.example.aiteachingapp.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars

// ─── colours used only on this screen ────────────────────────────────────────
private val MenuBg     = Color(0xFF08080E)
private val GlowOrange = Color(0x30DA7756)
private val CardBorder = Color(0xFF2A2A35)
private val TagGreen   = Color(0xFF10B981)
private val TagOrange  = ClaudeOrange
private val TagGrey    = Color(0xFF4B5563)

@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onOpenProject: (tutorialId: String, stepIndex: Int) -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val isEn = language == AppLanguage.EN

    // Refresh saved progress every time this screen is shown
    LaunchedEffect(Unit) { viewModel.refresh() }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding    = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBg)
    ) {
        // ── Ambient glow behind header ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(GlowOrange, Color.Transparent),
                        radius = 600f
                    )
                )
                .blur(60.dp)
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Push header below the status bar
                    .padding(top = statusBarPadding.calculateTopPadding())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language toggle
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1A1A25), RoundedCornerShape(20.dp))
                        .padding(3.dp)
                ) {
                    listOf(AppLanguage.VN to "VN", AppLanguage.EN to "EN").forEach { (lang, label) ->
                        val selected = language == lang
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) ClaudeOrange else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { if (!selected) viewModel.toggleLanguage() }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else DarkTextSecondary
                            )
                        }
                    }
                }

                // "AI LEARNING" wordmark
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "AI LEARNING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ClaudeOrange
                    )
                    Text(
                        text = if (isEn) "powered by Claude" else "cùng với Claude",
                        fontSize = 9.sp,
                        color = DarkTextHint,
                        letterSpacing = 1.sp
                    )
                }
            }

            // ── Hero title ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 32.dp)
            ) {
                Text(
                    text = if (isEn) "BUILD REAL" else "LẬP TRÌNH",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    color = DarkText,
                    lineHeight = 44.sp
                )
                Text(
                    text = if (isEn) "APPS WITH AI" else "VỚI AI THẬT",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    color = ClaudeOrange,
                    lineHeight = 44.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (isEn)
                        "Learn to code by building real projects using Claude"
                    else
                        "Học lập trình bằng cách xây dựng dự án thực với Claude",
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                    lineHeight = 19.sp
                )
            }

            // ── Section label ─────────────────────────────────────────────────
            Text(
                text = if (isEn) "PROJECTS" else "DỰ ÁN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = DarkTextHint,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // ── Project list ──────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects) { item ->
                    ProjectCard(
                        item = item,
                        isEn = isEn,
                        onClick = { onOpenProject(item.tutorial.tutorialId, item.savedStepIndex) },
                        onRestart = { viewModel.resetProgress(item.tutorial.tutorialId) }
                    )
                }

                // Locked "more coming soon" placeholder
                item {
                    LockedProjectCard(isEn = isEn)
                }

                // Bottom padding: nav bar + Clawd crab height so it doesn't overlap last card
                item {
                    Spacer(Modifier.height(220.dp + navBarPadding.calculateBottomPadding()))
                }
            }
        }

        // ── Clawd — fixed bottom-center, layered over everything ─────────────
        ClawdCornerWalker(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarPadding.calculateBottomPadding() + 8.dp)
        )
    }
}

// ── Project card ──────────────────────────────────────────────────────────────
@Composable
private fun ProjectCard(
    item: MenuViewModel.ProjectItem,
    isEn: Boolean,
    onClick: () -> Unit,
    onRestart: () -> Unit = {}
) {
    var showRestartConfirm by remember { mutableStateOf(false) }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            containerColor = Color(0xFF14141F),
            title = {
                Text(
                    text = if (isEn) "Restart project?" else "Bắt đầu lại dự án?",
                    color = DarkText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isEn) "Your progress will be reset to step 1." else "Tiến trình của bạn sẽ về lại bước 1.",
                    color = DarkTextSecondary
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .background(ClaudeOrange, RoundedCornerShape(20.dp))
                        .clickable {
                            showRestartConfirm = false
                            onRestart()
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isEn) "Restart" else "Bắt đầu lại",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clickable { showRestartConfirm = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isEn) "Cancel" else "Huỷ",
                        color = DarkTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        )
    }
    val borderColor = when {
        item.isComplete   -> TagGreen.copy(alpha = 0.5f)
        item.isInProgress -> ClaudeOrange.copy(alpha = 0.5f)
        else              -> CardBorder
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .background(Color(0xFF10101A), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEn) item.tutorial.title.en else item.tutorial.title.vn,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isEn) "${item.totalSteps} steps" else "${item.totalSteps} bước",
                        fontSize = 11.sp,
                        color = DarkTextHint
                    )
                }
                Spacer(Modifier.width(12.dp))
                StatusTag(item = item, isEn = isEn)
            }

            Spacer(Modifier.height(14.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF1E1E2E), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.progressFraction.coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ClaudeOrange, Color(0xFFFFB347))
                            ),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            Spacer(Modifier.height(12.dp))

            // Bottom row: step count + buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        item.isComplete   -> if (isEn) "✓ Completed!" else "✓ Hoàn thành!"
                        item.isNotStarted -> if (isEn) "Ready to start" else "Sẵn sàng bắt đầu"
                        else -> if (isEn) "Step ${item.displayStep} of ${item.totalSteps}"
                                else "Bước ${item.displayStep} / ${item.totalSteps}"
                    },
                    fontSize = 11.sp,
                    color = if (item.isComplete) TagGreen else DarkTextSecondary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Restart button — only shown when there's progress to reset
                    if (!item.isNotStarted) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFF3A3A4A), RoundedCornerShape(20.dp))
                                .clickable { showRestartConfirm = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = if (isEn) "↺ Restart" else "↺ Làm lại",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkTextSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                if (item.isComplete) TagGreen else ClaudeOrange,
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = when {
                                item.isComplete   -> if (isEn) "Review →" else "Xem lại →"
                                item.isNotStarted -> if (isEn) "Start →" else "Bắt đầu →"
                                else              -> if (isEn) "Continue →" else "Tiếp tục →"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTag(item: MenuViewModel.ProjectItem, isEn: Boolean) {
    val (bg, text) = when {
        item.isComplete   -> TagGreen.copy(alpha = 0.15f) to TagGreen
        item.isInProgress -> ClaudeOrange.copy(alpha = 0.15f) to ClaudeOrange
        else              -> TagGrey.copy(alpha = 0.2f) to TagGrey
    }
    val label = when {
        item.isComplete   -> if (isEn) "DONE" else "XONG"
        item.isInProgress -> if (isEn) "ACTIVE" else "ĐANG HỌC"
        else              -> if (isEn) "NEW" else "MỚI"
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = text
        )
    }
}

@Composable
private fun LockedProjectCard(isEn: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E1E2A), RoundedCornerShape(14.dp))
            .background(Color(0xFF0C0C15), RoundedCornerShape(14.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isEn) "More projects coming soon" else "Sắp có thêm dự án mới",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF3A3A4A)
            )
        }
    }
}

// ── Clawd typing in the corner ────────────────────────────────────────────────
@Composable
private fun ClawdCornerWalker(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(ctx)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }

    AsyncImage(
        model = ImageRequest.Builder(ctx)
            .data(R.drawable.clawd_typing)
            .build(),
        imageLoader = loader,
        contentDescription = "Clawd coding",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(420.dp)
    )
}
