package com.example.aiteachingapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aiteachingapp.data.applyUserInputs
import com.example.aiteachingapp.ui.components.*
import com.example.aiteachingapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(viewModel: TutorialViewModel, onBack: () -> Unit = {}) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val totalSteps by viewModel.totalSteps.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isTyping by viewModel.isAiTyping.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val currentLineIndex by viewModel.currentCodeLineIndex.collectAsStateWithLifecycle()
    val isStepComplete by viewModel.isStepComplete.collectAsStateWithLifecycle()
    val isAnimating by viewModel.isAnimating.collectAsStateWithLifecycle()
    val selectedSnippetIndex by viewModel.selectedSnippetIndex.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val showInterstitial by viewModel.showInterstitial.collectAsStateWithLifecycle()
    val awaitingChoice by viewModel.awaitingChoice.collectAsStateWithLifecycle()
    val awaitingCodeWrite by viewModel.awaitingCodeWrite.collectAsStateWithLifecycle()
    val cumulativeSnippets by viewModel.cumulativeSnippets.collectAsStateWithLifecycle()
    val animationStartLineIndex by viewModel.animationStartLineIndex.collectAsStateWithLifecycle()
    val userInputs by viewModel.userInputs.collectAsStateWithLifecycle()
    // Student's saved values injected into the code shown on the right-hand panel.
    val injectedSnippets = remember(cumulativeSnippets, userInputs) {
        cumulativeSnippets.map { it.copy(code = applyUserInputs(it.code, userInputs)) }
    }

    val isEn = language == AppLanguage.EN
    val isBug = currentStep.type == "bug"
    val isFinal = currentStep.type == "final"
    val progress = currentStep.stepNumber.toFloat() / totalSteps.toFloat()
    val context = LocalContext.current

    val stepTitle = if (isEn) currentStep.title.en else currentStep.title.vn
    val rationale = if (isEn) currentStep.promptRationale.en else currentStep.promptRationale.vn

    // Top instruction card has its own scroll state; jump back to the top each
    // time the step changes so users always see the title/first line.
    val instructionScroll = rememberScrollState()
    // Track whether the student has copied the prompt yet.
    var promptCopied by remember(currentStep.stepNumber) { mutableStateOf(false) }
    LaunchedEffect(currentStep.stepNumber) {
        instructionScroll.scrollTo(0)
    }

    // ── Card weight system — four phases ─────────────────────────────────────
    //
    // INITIAL      instruction big, Claude mini (header+input only), code tiny
    // CLAUDE_FOCUS Claude expands as text streams in, instruction collapses to tab
    // CODE_WRITING Claude shrinks as code grows, synced to 160ms/line animation
    // COMPLETE     instruction tab, Claude tab, code fills screen
    //
    // Tapping a collapsed tab re-expands it (resets on step change).
    var claudeUserExpanded       by remember(currentStep.stepNumber) { mutableStateOf(false) }
    var instructionUserExpanded  by remember(currentStep.stepNumber) { mutableStateOf(false) }

    // Code is ACTUALLY writing lines (not just "animation job running")
    val codeActuallyAnimating = (isAnimating || isStepComplete) && currentLineIndex >= animationStartLineIndex

    // Code animation progress 0→1 (drives CODE_WRITING phase)
    val totalSnippetLines = cumulativeSnippets.getOrNull(selectedSnippetIndex)?.codeLines?.size ?: 0
    val totalNewLines = (totalSnippetLines - animationStartLineIndex).coerceAtLeast(1)
    val codeAnimProgress: Float = when {
        isStepComplete -> 1f
        codeActuallyAnimating ->
            ((currentLineIndex - animationStartLineIndex + 1).toFloat() / totalNewLines).coerceIn(0f, 1f)
        else -> 0f
    }

    // Phase flags — order matters for card weight logic
    // claudeFocused starts the INSTANT isAnimating fires (before isTyping even sets),
    // so card3 is pinned immediately and card2 grows upward from the start.
    val claudeFocused = (isAnimating || isTyping || streamingText.isNotEmpty() || awaitingChoice || awaitingCodeWrite) &&
                        !isStepComplete && !codeActuallyAnimating
    val codeWriting   = codeActuallyAnimating && !isStepComplete
    val isInitial     = !isAnimating && !isStepComplete && !claudeFocused

    // ── Card weights ──────────────────────────────────────────────────────────
    //
    // Rule: only ONE card animates at a time; the other two are either fixed or
    // derived. This ensures the growth always goes in the right direction.
    //
    // INITIAL       card1=0.60  card2=0.20(mini)  card3=0.20
    // CLAUDE_FOCUS  card3 FIXED=0.20; card1 tween→0.06; card2 fills the rest
    //               → card2 grows UPWARD as card1's bottom rises
    // CODE_WRITING  card1 FIXED=0.06; card2 shrinks via codeAnimProgress; card3 fills
    //               → card3 grows UPWARD as card2's bottom rises
    // COMPLETE      card1=0.06  card2=0.08  card3=0.86

    val instrTarget = when {
        !isInitial && instructionUserExpanded -> 0.42f
        isInitial                             -> 0.60f
        else                                  -> 0.06f
    }
    val card1Weight by animateFloatAsState(instrTarget, tween(500), label = "card1")

    val card2Weight: Float
    val card3Weight: Float
    when {
        isStepComplete -> {
            card2Weight = if (claudeUserExpanded) 0.50f else 0.08f
            card3Weight = (1f - card1Weight - card2Weight).coerceAtLeast(0.05f)
        }
        codeWriting -> {
            // card1 already at 0.06; card2 shrinks; card3 absorbs the freed space
            card2Weight = lerp(0.74f, 0.08f, codeAnimProgress)
            card3Weight = (1f - 0.06f - card2Weight).coerceAtLeast(0.05f)
        }
        claudeFocused -> {
            // card3 pinned; card1 tween collapses; card2 absorbs freed space upward
            card3Weight = 0.20f
            card2Weight = (1f - card1Weight - 0.20f).coerceAtLeast(0.15f)
        }
        else -> {
            card2Weight = 0.20f
            card3Weight = 0.20f
        }
    }

    // Derived display flags
    val claudeIsCollapsed = (codeWriting && codeAnimProgress > 0.7f) || (isStepComplete && !claudeUserExpanded)

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.getTutorialTitle(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = DarkText
                        )
                        Text(
                            text = "${if (isEn) "Step" else "Bước"} ${currentStep.stepNumber}/$totalSteps: $stepTitle",
                            fontSize = 10.sp,
                            color = DarkTextSecondary,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(DarkSurface3, RoundedCornerShape(20.dp))
                            .padding(2.dp)
                    ) {
                        listOf(AppLanguage.VN to "VN", AppLanguage.EN to "EN").forEach { (lang, label) ->
                            val selected = language == lang
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (selected) ClaudeOrange else Color.Transparent,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                TextButton(
                                    onClick = { if (!selected) viewModel.toggleLanguage() },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(20.dp)
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
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface2)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBg)
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (isBug) Color(0xFFEF4444) else ClaudeOrange,
                trackColor = DarkSurface3
            )

            // Step dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface2)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalSteps) { index ->
                    val step = index + 1
                    val isDone = step < currentStep.stepNumber
                    val isCurrent = step == currentStep.stepNumber
                    val size = if (isCurrent) 22.dp else 16.dp
                    Box(
                        modifier = Modifier
                            .size(size)
                            .background(
                                when {
                                    isCurrent && isBug -> Color(0xFFEF4444)
                                    isCurrent         -> ClaudeOrange
                                    isDone            -> Color(0xFF10B981)
                                    else              -> DarkSurface3
                                },
                                RoundedCornerShape(size / 2)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCurrent || isDone) {
                            Text(
                                text = if (isDone) "✓" else step.toString(),
                                fontSize = if (isCurrent) 10.sp else 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            if (isFinal) {
                CompletionScreen(
                    language = language,
                    onInstallApp = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://app-updates.mcubittbuilders.workers.dev"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {

                    // CARD 1: Instruction + Prompt — collapses to a thin bar
                    // once the student has copied the prompt.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(card1Weight)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBug) DarkBugSurface else DarkSurface
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        if (!isInitial && !instructionUserExpanded) {
                            // Collapsed tab — tap to re-expand
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(DarkSurface2)
                                    .clickable { instructionUserExpanded = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stepTitle,
                                    color = DarkText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Text("▼", fontSize = 11.sp, color = DarkTextSecondary)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(instructionScroll)
                                    .padding(10.dp)
                            ) {
                                // Show a collapse handle when manually expanded mid-session
                                if (instructionUserExpanded && !isInitial) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { instructionUserExpanded = false }
                                            .padding(bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("▲", fontSize = 10.sp, color = DarkTextSecondary)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isEn) "Collapse" else "Thu gọn",
                                            fontSize = 10.sp,
                                            color = DarkTextSecondary
                                        )
                                    }
                                }
                                if (isBug) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🐛 ", fontSize = 14.sp)
                                        Text(
                                            text = stepTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFCA5A5)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = stepTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkText
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isEn) currentStep.instruction.en else currentStep.instruction.vn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                // Editable "paste your own info" card (e.g. Firebase URL).
                                if (currentStep.userInputs.isNotEmpty()) {
                                    CredentialInputCard(
                                        fields = currentStep.userInputs,
                                        values = userInputs,
                                        isEnglish = isEn,
                                        onValueChange = { key, value -> viewModel.setUserInput(key, value) }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                val promptTextLocalized = applyUserInputs(
                                    if (isEn) currentStep.promptText.en else currentStep.promptText.vn,
                                    userInputs
                                )
                                if (promptTextLocalized.isNotBlank()) {
                                    CopyablePromptBox(
                                        promptText = promptTextLocalized,
                                        rationale = rationale,
                                        label = when {
                                            isBug && isEn -> "Error Code — Copy & Paste into chat"
                                            isBug         -> "Mã lỗi — Sao chép & Dán vào chat"
                                            isEn          -> "AI Prompt — Copy & Paste into chat"
                                            else          -> "Prompt — Sao chép & Dán vào chat"
                                        },
                                        isBug = isBug,
                                        onCopied = { promptCopied = true }
                                    )
                                }
                            }
                        }
                    }

                    // CARD 2: Claude Chat
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(card2Weight)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .then(
                                if (claudeIsCollapsed) Modifier.clickable { claudeUserExpanded = true }
                                else Modifier
                            ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(0.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        ClaudeChatSimulator(
                            messages = chatMessages,
                            isTyping = isTyping,
                            streamingText = streamingText,
                            isAnimating = isAnimating,
                            isCollapsed = claudeIsCollapsed,
                            isMini = isInitial,
                            placeholder = if (isBug) {
                                if (isEn) "Paste error code here..." else "Dán mã lỗi vào đây..."
                            } else {
                                if (isEn) "Paste prompt here..." else "Dán prompt vào đây..."
                            },
                            onSendMessage = { viewModel.onSendMessage(it) },
                            answerChoices = currentStep.answerChoices.map {
                                if (isEn) it.en else it.vn
                            },
                            awaitingChoice = awaitingChoice,
                            onPickChoice = { viewModel.pickAnswerChoice(it) },
                            awaitingCodeWrite = awaitingCodeWrite,
                            onWriteCode = { viewModel.startCodeWrite() },
                            isEnglish = isEn
                        )
                    }

                    // CARD 3: Code Panel — also expands when Card 1 collapses
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(card3Weight)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        CodeDisplayPanel(
                            snippets = injectedSnippets,
                            selectedSnippetIndex = selectedSnippetIndex,
                            currentLineIndex = currentLineIndex,
                            language = language,
                            onSelectSnippet = { viewModel.selectSnippet(it) }
                        )
                    }
                }
            }

            // Navigation footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface2)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentStep.stepNumber <= 1) viewModel.resetCurrentStep()
                        else viewModel.goToPreviousStep()
                    },
                    enabled = !isAnimating,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkText)
                ) {
                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (isEn) "Back" else "Trước", fontSize = 12.sp)
                }

                AnimatedVisibility(
                    visible = isStepComplete || isFinal,
                    enter = fadeIn() + slideInVertically { it }
                ) {
                    Button(
                        onClick = { viewModel.requestNextStep() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isBug  -> Color(0xFF7C3AED)
                                isFinal -> Color(0xFF10B981)
                                else   -> Color(0xFF10B981)
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        val isLast = currentStep.stepNumber == totalSteps
                        Text(
                            text = when {
                                isLast -> if (isEn) "Finish!" else "Hoàn thành!"
                                isBug  -> if (isEn) "Bug Fixed! Next →" else "Đã sửa! Tiếp →"
                                else   -> if (isEn) "Next Step →" else "Bước tiếp →"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!isStepComplete && !isFinal) {
                    Text(
                        text = if (isAnimating) {
                            if (isEn) "AI thinking..." else "AI đang xử lý..."
                        } else {
                            if (isEn) "Send prompt to continue" else "Gửi prompt để tiếp tục"
                        },
                        fontSize = 10.sp,
                        color = DarkTextHint
                    )
                }
            }
        }
    }

        // Between-step interstitial: big Clawd + speech bubble
        if (showInterstitial) {
            ClawdInterstitial(
                explanationText = if (isEn) currentStep.clawdExplanation.en
                                  else currentStep.clawdExplanation.vn,
                isEnglish = isEn,
                isLastStep = currentStep.stepNumber + 1 == totalSteps,
                onContinue = { viewModel.dismissInterstitialAndAdvance() },
                vocabTerms = currentStep.vocabTerms,
                codeExplainerText = if (isEn) currentStep.codeExplainer.en else currentStep.codeExplainer.vn
            )
        }
    }
}
