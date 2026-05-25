package com.example.aiteachingapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.aiteachingapp.R
import com.example.aiteachingapp.data.models.VocabTerm
import com.example.aiteachingapp.ui.theme.*
import kotlinx.coroutines.delay

@Composable
private fun rememberGifLoader(): ImageLoader {
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

/**
 * Between-step interstitial: big Clawd walks in, a speech bubble pops with an
 * explanation of what we just did + a teaser for the next step.
 *
 * The explanation text is rendered through [buildSyntaxHighlighted] so the
 * author can highlight inline code-ish words by surrounding them with backticks
 * (e.g. "We used `val` to make a new variable.").
 */
@Composable
fun ClawdInterstitial(
    explanationText: String,
    isEnglish: Boolean,
    isLastStep: Boolean,
    onContinue: () -> Unit,
    vocabTerms: List<VocabTerm> = emptyList(),
    codeExplainerText: String = ""
) {
    val loader = rememberGifLoader()
    val ctx = LocalContext.current

    var showBubble by remember { mutableStateOf(false) }
    var showVocab  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        showBubble = true
        delay(400)
        if (vocabTerms.isNotEmpty()) showVocab = true
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Scrollable content — button lives at the bottom so user must scroll to reach it
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp, start = 16.dp, end = 16.dp)
                .padding(bottom = navBarPadding.calculateBottomPadding() + 24.dp)
        ) {
            // Speech bubble
            AnimatedVisibility(
                visible = showBubble,
                enter = fadeIn(tween(350)) + scaleIn(initialScale = 0.85f)
            ) {
                SpeechBubble(text = explanationText)
            }

            // Code explainer card
            if (codeExplainerText.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                AnimatedVisibility(
                    visible = showVocab,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
                ) {
                    CodeExplainerCard(text = codeExplainerText, isEnglish = isEnglish)
                }
            }

            // Vocab card
            if (vocabTerms.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AnimatedVisibility(
                    visible = showVocab,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 2 }
                ) {
                    VocabCard(terms = vocabTerms, isEnglish = isEnglish)
                }
            }

            // Clawd + Next button row at the very bottom — must scroll to reach
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(
                visible = showVocab,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 2 }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = (-16).dp), // bleed to left edge (counters column's start padding)
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clawd peeking from the left edge, flipped to face right toward the button
                    val clawdSize = 220.dp
                    val peekShow  = 110.dp
                    Box(
                        modifier = Modifier
                            .size(width = peekShow, height = clawdSize)
                            .clip(RoundedCornerShape(0.dp))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx)
                                .data(R.drawable.clawd_walk)
                                .build(),
                            imageLoader = loader,
                            contentDescription = "Clawd peeking",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(clawdSize)
                                .align(Alignment.CenterStart)
                                .graphicsLayer(scaleX = -1f) // flip to face right
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Next button — at the bottom of the scroll, past all content
                    Button(
                        onClick = onContinue,
                        colors = ButtonDefaults.buttonColors(containerColor = ClaudeOrange),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = when {
                                isLastStep && isEnglish  -> "Let's finish! →"
                                isLastStep               -> "Hoàn thành nào! →"
                                isEnglish                -> "Next step →"
                                else                     -> "Bước tiếp →"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Code explainer card ───────────────────────────────────────────────────────

@Composable
private fun CodeExplainerCard(text: String, isEnglish: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E1A2B), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = if (isEnglish) "WHAT JUST HAPPENED?" else "VỪA XẢY RA GÌ?",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = Color(0xFF60A5FA),
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = DarkText,
            lineHeight = 22.sp
        )
    }
}

// ── Vocab card ────────────────────────────────────────────────────────────────

@Composable
private fun VocabCard(terms: List<VocabTerm>, isEnglish: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121C), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isEnglish) "KEY TERMS" else "THUẬT NGỮ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = ClaudeOrange,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(12.dp))
        terms.forEachIndexed { i, term ->
            if (i > 0) {
                // Thin divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF1E1E2E))
                )
                Spacer(Modifier.height(10.dp))
            }
            VocabRow(term = term, isEnglish = isEnglish)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun VocabRow(term: VocabTerm, isEnglish: Boolean) {
    val chipColor = try {
        val hex = term.color.trimStart('#')
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (e: Exception) { ClaudeOrange }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Colour chip with the term
        Box(
            modifier = Modifier
                .background(chipColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = term.term,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = chipColor
            )
        }
        Spacer(Modifier.width(12.dp))
        // Explanation
        Text(
            text = if (isEnglish) term.explanation.en else term.explanation.vn,
            fontSize = 13.sp,
            color = DarkTextSecondary,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SpeechBubble(text: String) {
    val highlighted = remember(text) { buildSyntaxHighlighted(text) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 360.dp)
            .background(DarkSurface2, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Clawd says...",
                    color = ClaudeOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = highlighted,
                color = DarkText,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontFamily = FontFamily.Default
            )
        }
        // Tail pointing down-right towards Clawd peeking on the right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-48).dp, y = 8.dp)
                .size(width = 18.dp, height = 12.dp)
                .background(
                    color = DarkSurface2,
                    shape = GenericShape { size, _ ->
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width * 0.7f, size.height)
                        close()
                    }
                )
        )
    }
}
