package com.seyfbk.dynamicnotify.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.PushNotification

/**
 * The pill itself: a little cat sits at the leading edge at all times.
 * When [expanded] the title/message fade + expand in next to it, mimicking
 * the iOS "Dynamic Island" alert shape.
 */
@Composable
fun IslandPill(
    notification: PushNotification?,
    expanded: Boolean,
    critter: CritterKind,
    onClick: () -> Unit
) {
    val accent = notification?.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: Color(0xFFFF9D3D)

    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 26.dp else 20.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "corner"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .background(Color.Black, RoundedCornerShape(cornerRadius))
            .clickable(enabled = expanded) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .widthIn(min = 46.dp)
    ) {
        // Cat (or, occasionally, a little fish) always leads the pill.
        AnimatedCritterIcon(kind = critter, accent = accent, active = expanded)

        AnimatedVisibility(
            visible = expanded && notification != null,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Column(modifier = Modifier.widthIn(max = 220.dp)) {
                Text(
                    text = notification?.title.orEmpty(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (!notification?.message.isNullOrBlank()) {
                    Text(
                        text = notification?.message.orEmpty(),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

/**
 * The interactive mood check-in: cat leads, greeting text, three tappable
 * mood buttons. Stays expanded until the user answers.
 */
@Composable
fun AskPill(
    greeting: String,
    critter: CritterKind,
    onPick: (Mood) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .background(Color.Black, RoundedCornerShape(26.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        AnimatedCritterIcon(kind = critter, accent = Color(0xFFFF9D3D), active = true)

        Column(modifier = Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = greeting,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
