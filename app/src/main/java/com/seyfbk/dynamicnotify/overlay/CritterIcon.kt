package com.seyfbk.dynamicnotify.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Which little animal leads the pill this time. */
enum class CritterKind {
    CAT, FISH;

    companion object {
        /** Fish shows up occasionally instead of the cat. */
        fun random(fishChance: Double = 0.15): CritterKind =
            if (Random.nextDouble() < fishChance) FISH else CAT
    }
}

@Composable
fun AnimatedCritterIcon(kind: CritterKind, accent: Color, active: Boolean) {
    Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        when (kind) {
            CritterKind.CAT -> AnimatedCat(accent = accent, active = active)
            CritterKind.FISH -> AnimatedFish(active = active)
        }
    }
}

@Composable
private fun AnimatedCat(accent: Color, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "cat")

    // Ears wiggle gently back and forth.
    val earAngle by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "ear-wiggle"
    )

    // A natural-feeling blink: eyes open most of the time, quick dip near the
    // end of each cycle.
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2600
                1f at 0
                1f at 2250
                0.1f at 2400
                1f at 2600
            }
        ),
        label = "blink"
    )

    Canvas(modifier = Modifier.size(if (active) 26.dp else 22.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Ears (two small triangles, wiggling).
        rotate(-earAngle, pivot = Offset(center.x - r * 0.5f, center.y - r * 0.6f)) {
            drawPath(earPath(center, r, leftSide = true), color = accent)
        }
        rotate(earAngle, pivot = Offset(center.x + r * 0.5f, center.y - r * 0.6f)) {
            drawPath(earPath(center, r, leftSide = false), color = accent)
        }

        // Head.
        drawCircle(color = accent, radius = r * 0.85f, center = center)

        // Eyes (scaled vertically by blink amount).
        val eyeY = center.y - r * 0.05f
        val eyeDx = r * 0.32f
        val eyeR = r * 0.14f
        listOf(-1f, 1f).forEach { side ->
            scale(1f, blink.coerceAtLeast(0.05f), pivot = Offset(center.x + side * eyeDx, eyeY)) {
                drawCircle(color = Color.Black, radius = eyeR, center = Offset(center.x + side * eyeDx, eyeY))
            }
        }

        // Nose.
        val nosePath = Path().apply {
            moveTo(center.x - r * 0.08f, center.y + r * 0.22f)
            lineTo(center.x + r * 0.08f, center.y + r * 0.22f)
            lineTo(center.x, center.y + r * 0.34f)
            close()
        }
        drawPath(nosePath, color = Color.Black)
    }
}

private fun earPath(center: Offset, r: Float, leftSide: Boolean): Path {
    val dir = if (leftSide) -1f else 1f
    return Path().apply {
        moveTo(center.x + dir * r * 0.15f, center.y - r * 0.55f)
        lineTo(center.x + dir * r * 0.75f, center.y - r * 1.15f)
        lineTo(center.x + dir * r * 0.85f, center.y - r * 0.35f)
        close()
    }
}

@Composable
private fun AnimatedFish(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "fish")

    // Whole body sways side to side like it's swimming in place.
    val sway by transition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "swim-sway"
    )

    // Tail flicks a bit faster than the body sways.
    val tailAngle by transition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "tail-flick"
    )

    Canvas(modifier = Modifier.size(if (active) 26.dp else 22.dp)) {
        val w = size.width
        val h = size.height
        val bodyCenter = Offset(w / 2f + sway.dp.toPx() / 4f, h / 2f)

        // Tail fin, hinged at the back of the body.
        val tailHinge = Offset(bodyCenter.x - w * 0.28f, bodyCenter.y)
        rotate(tailAngle, pivot = tailHinge) {
            val tail = Path().apply {
                moveTo(tailHinge.x, tailHinge.y)
                lineTo(tailHinge.x - w * 0.28f, tailHinge.y - h * 0.22f)
                lineTo(tailHinge.x - w * 0.28f, tailHinge.y + h * 0.22f)
                close()
            }
            drawPath(tail, color = Color.Black)
        }

        // Body.
        drawOval(
            color = Color.Black,
            topLeft = Offset(bodyCenter.x - w * 0.28f, bodyCenter.y - h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.44f)
        )

        // Eye.
        drawCircle(
            color = Color.White,
            radius = w * 0.045f,
            center = Offset(bodyCenter.x + w * 0.14f, bodyCenter.y - h * 0.04f)
        )
    }
}
