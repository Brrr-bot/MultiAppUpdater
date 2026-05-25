package com.example.aiteachingapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.aiteachingapp.R
import com.example.aiteachingapp.data.models.ChatMessage
import com.example.aiteachingapp.ui.theme.*
import kotlinx.coroutines.launch

/** Shared Coil ImageLoader that knows how to decode GIFs. */
@Composable
private fun rememberGifImageLoader(): ImageLoader {
    val ctx = LocalContext.current
    return remember {
        ImageLoader.Builder(ctx)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
}

/** Static idle Clawd avatar used inside chat bubbles. */
@Composable
fun ClaudeAvatar(size: Int) {
    val loader = rememberGifImageLoader()
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx)
            .data(R.drawable.clawd_idle)
            .build(),
        imageLoader = loader,
        contentDescription = "Clawd",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size.dp)
    )
}

/** Header strip with Clawd crab-walking back and forth behind the "Claude" label. */
@Composable
private fun ClawdWalkingHeader() {
    val loader = rememberGifImageLoader()
    val ctx = LocalContext.current

    val transition = rememberInfiniteTransition(label = "clawd_walk")
    // 0f -> 1f -> 0f bounce
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "walk_progress"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(DarkSurface2)
    ) {
        val maxPx = maxWidth - 32.dp
        val walking = progress > 0f
        // Flip horizontally when walking right-to-left (reverse phase)
        // RepeatMode.Reverse means the second half plays backwards in value space,
        // so animated value alone doesn't tell us direction. We approximate with sin.
        val goingLeft = remember { mutableStateOf(false) }
        // Track previous value to detect direction
        var previous by remember { mutableStateOf(0f) }
        LaunchedEffect(progress) {
            goingLeft.value = progress < previous
            previous = progress
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        ) {
            ClaudeAvatar(size = 22)
            Spacer(Modifier.width(8.dp))
            Text(
                "Claude",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = DarkText
            )
        }

        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(R.drawable.clawd_walk)
                .build(),
            imageLoader = loader,
            contentDescription = "Clawd walking",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = -(maxPx * (1f - progress)) / 2)
                .size(28.dp)
                .graphicsLayer { scaleX = if (goingLeft.value) -1f else 1f }
        )
    }
}

@Composable
fun ClaudeChatSimulator(
    messages: List<ChatMessage>,
    isTyping: Boolean,
    streamingText: String,
    isAnimating: Boolean,
    /** Show only the walking Clawd header (no messages, no input) — used when fully collapsed. */
    isCollapsed: Boolean = false,
    /** Show header + input only (no messages list) — used in the initial pre-send state. */
    isMini: Boolean = false,
    onSendMessage: (String) -> Unit,
    placeholder: String = "Dán prompt vào đây...",
    answerChoices: List<String> = emptyList(),
    awaitingChoice: Boolean = false,
    onPickChoice: (String) -> Unit = {},
    awaitingCodeWrite: Boolean = false,
    onWriteCode: () -> Unit = {},
    isEnglish: Boolean = false,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Dismiss keyboard the moment text appears in the field (paste = instant hide)
    LaunchedEffect(inputText) {
        if (inputText.isNotBlank()) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(messages.size, isTyping, streamingText, awaitingChoice, awaitingCodeWrite) {
        if (messages.isNotEmpty() || isTyping || streamingText.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1
                )
            }
        }
    }

    // Shared input row composable used in both mini and full modes
    @Composable
    fun InputRow() {
        HorizontalDivider(color = DarkDivider, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface2)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .background(DarkSurface3, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                textStyle = TextStyle(fontSize = 14.sp, color = DarkText),
                decorationBox = { inner ->
                    if (inputText.isEmpty()) {
                        Text(placeholder, fontSize = 14.sp, color = DarkTextHint)
                    }
                    inner()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isAnimating) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onSendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (inputText.isNotBlank() && !isAnimating) ClaudeOrange else DarkSurface3,
                        CircleShape
                    )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Gửi", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (isCollapsed) {
        // Fully collapsed: just the animated Clawd header tab
        Column(modifier = modifier.fillMaxSize().background(DarkSurface)) {
            ClawdWalkingHeader()
        }
    } else if (isMini) {
        // Mini: header + input only — no messages list
        Column(modifier = modifier.fillMaxSize().background(DarkSurface)) {
            ClawdWalkingHeader()
            InputRow()
        }
    } else {
        // Full: header + messages + input
        Column(modifier = modifier.fillMaxSize().background(DarkSurface)) {
            ClawdWalkingHeader()
            HorizontalDivider(color = DarkDivider, thickness = 0.5.dp)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .background(DarkBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg -> ChatBubble(message = msg) }
                if (isTyping) { item { ClawdTypingIndicator() } }
                if (streamingText.isNotEmpty()) { item { AiBubble(text = streamingText, isStreaming = true) } }
                if (awaitingChoice && answerChoices.isNotEmpty()) {
                    item { AnswerChoiceList(choices = answerChoices, onPick = onPickChoice) }
                }
                if (awaitingCodeWrite) {
                    item { WriteCodeButton(isEnglish = isEnglish, onClick = onWriteCode) }
                }
            }
            InputRow()
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    if (message.isUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .background(DarkUserBubble, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(message.text, fontSize = 13.sp, color = DarkText)
            }
        }
    } else {
        AiBubble(text = message.text, isStreaming = false)
    }
}

@Composable
private fun AiBubble(text: String, isStreaming: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        ClaudeAvatar(size = 24)
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .background(DarkAiBubble, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (isStreaming) "$text▌" else text,
                fontSize = 13.sp,
                color = DarkText,
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * Multi-choice answer buttons rendered inline at the bottom of the chat.
 * We only ever show the choice(s) we want the student to actually pick —
 * no decoys. Tapping one fires onPick(text) and the choice is added to
 * the chat as a user message.
 */
@Composable
private fun AnswerChoiceList(
    choices: List<String>,
    onPick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(32.dp))
            Text(
                text = "👇 Tap your answer",
                color = ClaudeOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        choices.forEach { choice ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp)
                    .background(ClaudeOrange, RoundedCornerShape(20.dp))
                    .clickable { onPick(choice) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = choice,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** "Write Code →" CTA shown after AI response on steps with no answer choices. */
@Composable
private fun WriteCodeButton(isEnglish: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .background(ClaudeOrange, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (isEnglish) "Write Code →" else "Viết Code →",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Clawd-at-keyboard GIF as the AI-typing indicator. */
@Composable
private fun ClawdTypingIndicator() {
    val loader = rememberGifImageLoader()
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        ClaudeAvatar(size = 24)
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(DarkAiBubble, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(R.drawable.clawd_typing)
                    .build(),
                imageLoader = loader,
                contentDescription = "Clawd typing",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
