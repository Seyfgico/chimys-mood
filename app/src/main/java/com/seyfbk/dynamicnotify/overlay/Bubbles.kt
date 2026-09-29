package com.seyfbk.dynamicnotify.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyfbk.dynamicnotify.data.Mood

private val BubbleBackground = Color(0xF01C1B22)
private val BubbleShape = RoundedCornerShape(18.dp)

private fun parseAccent(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFFFF9D3D))

/** A message from the cat. Tap it to dismiss. */
@Composable
fun MessageBubble(text: String, accentHex: String, onClick: () -> Unit) {
    val accent = remember(accentHex) { parseAccent(accentHex) }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BubbleBackground, BubbleShape)
                .border(1.5.dp, accent, BubbleShape)
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        }
    }
}

/** The mood check-in: a greeting and three answer buttons. Stays until answered. */
@Composable
fun AskBubble(greeting: String, onPick: (Mood) -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BubbleBackground, BubbleShape)
                .border(1.5.dp, Color(0xFFFF9D3D), BubbleShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = greeting,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoodButton("Too Bad", Color(0xFF7C8CE0)) { onPick(Mood.TOO_BAD) }
                MoodButton("Normal", Color(0xFFB0B0B0)) { onPick(Mood.NORMAL) }
                MoodButton("Good", Color(0xFFFF9D3D)) { onPick(Mood.GOOD) }
            }
        }
    }
}

@Composable
private fun MoodButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = label, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
