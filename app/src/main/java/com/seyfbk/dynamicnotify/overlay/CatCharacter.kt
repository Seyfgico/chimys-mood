package com.seyfbk.dynamicnotify.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.seyfbk.dynamicnotify.data.Mood
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Things the cat does now and then, on its own. */
enum class CatAction(val durationMs: Int) {
    HOP(650),          // quick little hop — used when a message arrives or the cat is tapped
    JUMP(1200),        // two proper jumps
    SPIN(1100),        // jump + full spin
    PLAY(3400),        // bats a ball around
    LOOK_AROUND(2800), // eyes and head wander
    STRETCH(2000),     // stretch + yawn
    SLEEP(6500),       // curls up, eyes closed, floating Zs
    SIGH(2800)         // head droops, a tear rolls
}

/** What the cat tends to do depends on how Lina is feeling. */
private fun pickAction(mood: Mood): CatAction {
    val weights: List<Pair<CatAction, Int>> = when (mood) {
        Mood.GOOD -> listOf(
            CatAction.JUMP to 5, CatAction.PLAY to 4, CatAction.SPIN to 2,
            CatAction.LOOK_AROUND to 2, CatAction.STRETCH to 1
        )
        Mood.NORMAL -> listOf(
            CatAction.LOOK_AROUND to 4, CatAction.STRETCH to 3, CatAction.SLEEP to 2,
            CatAction.PLAY to 2, CatAction.JUMP to 1
        )
        Mood.TOO_BAD -> listOf(
            CatAction.SIGH to 4, CatAction.SLEEP to 4,
            CatAction.LOOK_AROUND to 2, CatAction.STRETCH to 1
        )
    }
    var roll = Random.nextInt(weights.sumOf { it.second })
    for ((action, weight) in weights) {
        if (roll < weight) return action
        roll -= weight
    }
    return CatAction.LOOK_AROUND
}

/**
 * The on-screen cat. [mood] sets its baseline personality (happy = perky
 * ears and a fast tail, sad = drooped ears and a slow tail), and it picks
 * random actions that fit that mood. Bump [hopSignal] to make it hop.
 */
@Composable
fun CatCharacter(mood: Mood?, hopSignal: Int, modifier: Modifier = Modifier) {
    val effectiveMood = mood ?: Mood.NORMAL

    val infinite = rememberInfiniteTransition(label = "cat-base")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val blink by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3200
                1f at 0
                1f at 2900
                0.08f at 3040
                1f at 3200
            }
        ),
        label = "blink"
    )

    var action by remember { mutableStateOf<CatAction?>(null) }
    val progress = remember { Animatable(0f) }
    val lastHop = remember { mutableIntStateOf(0) }

    LaunchedEffect(effectiveMood, hopSignal) {
        try {
            if (hopSignal != lastHop.intValue) {
                lastHop.intValue = hopSignal
                action = CatAction.HOP
                progress.snapTo(0f)
                progress.animateTo(1f, tween(CatAction.HOP.durationMs, easing = LinearEasing))
                action = null
            }
            while (true) {
                delay(Random.nextLong(2500L, 5500L))
                val next = pickAction(effectiveMood)
                action = next
                progress.snapTo(0f)
                progress.animateTo(1f, tween(next.durationMs, easing = LinearEasing))
                action = null
                progress.snapTo(0f)
            }
        } finally {
            action = null
        }
    }

    Canvas(modifier = modifier) {
        drawCat(effectiveMood, action, progress.value, phase, blink)
    }
}

// ------------------------------------------------------------------ drawing

private val FUR = Color(0xFF9C9CA8)
private val FUR_DARK = Color(0xFF7E7E8C)
private val FUR_LIGHT = Color(0xFFE8E8EC)
private val EAR_PINK = Color(0xFFF7B0C4)
private val OUTLINE = Color(0xFF3A3842)
private val NOSE = Color(0xFFD6789A)
private val TEAR = Color(0xFF8ED0FF)
private val BALL = Color(0xFFFF7AA2)
private val ZZZ = Color(0xFF8E8EF0)

private val PI_F = PI.toFloat()
private val TWO_PI_F = 2f * PI_F

/** Draws the whole cat in a 100 x 120 unit space, scaled to the canvas width. */
private fun DrawScope.drawCat(mood: Mood, action: CatAction?, p: Float, phase: Float, blink: Float) {
    val u = size.width / 100f
    val breathe = sin(phase * TWO_PI_F)
    val sad = mood == Mood.TOO_BAD
    val happy = mood == Mood.GOOD

    // ---- pose, starting from the mood's baseline
    var offY = if (sad) 2.5f else 0f
    var sqX = 1f
    var sqY = 1f + breathe * (if (sad) 0.006f else 0.012f)
    var rot = 0f
    var headTilt = if (sad) -3f else 0f
    var pupilX = 0f
    var eyesClosed = false
    var happyEyes = false
    var mouthOpen = 0f
    var earDroop = if (sad) 24f else 0f
    var earTwitch = 0f
    var tear = -1f
    var pawLift = 0f
    var zzz = -1f
    var ballPos: Offset? = null
    var jumpH = 0f

    when (action) {
        CatAction.HOP -> {
            val amp = if (sad) 0.45f else 1f
            val s = sin(PI_F * p)
            offY -= 11f * amp * s
            earDroop -= 9f * s
            happyEyes = !sad && s > 0.3f
            jumpH = 11f * amp * s
        }
        CatAction.JUMP -> {
            val h = abs(sin(TWO_PI_F * p)) // two hops
            offY -= 16f * h
            val g = 1f - h
            val squash = g * g * g * g * 0.10f
            sqX += squash - 0.03f * h
            sqY += -squash + 0.05f * h
            happyEyes = true
            mouthOpen = h * 0.6f
            earDroop -= 8f * h
            jumpH = 16f * h
        }
        CatAction.SPIN -> {
            val ease = p * p * (3f - 2f * p)
            val s = sin(PI_F * p)
            offY -= 14f * s
            rot = 360f * ease
            happyEyes = true
            mouthOpen = 0.4f
            jumpH = 14f * s
        }
        CatAction.PLAY -> {
            val swing = sin(TWO_PI_F * p * 2f)
            val bounce = abs(sin(TWO_PI_F * p * 4f))
            ballPos = Offset((50f + 30f * swing) * u, (106f - 14f * bounce) * u)
            headTilt += 9f * swing
            pupilX = 2.6f * swing
            pawLift = max(0f, sin(TWO_PI_F * p * 4f + 1f)) * 7f
        }
        CatAction.LOOK_AROUND -> {
            val look = sin(TWO_PI_F * p * 1.5f)
            pupilX = 3f * look
            headTilt += 6f * look
            earTwitch = 6f * sin(TWO_PI_F * p * 3f)
        }
        CatAction.STRETCH -> {
            val s = sin(PI_F * p)
            sqX += 0.16f * s
            sqY -= 0.10f * s
            mouthOpen = s
            eyesClosed = s > 0.45f
            headTilt -= 5f * s
        }
        CatAction.SLEEP -> {
            eyesClosed = true
            offY += 4f
            sqY -= 0.05f
            sqY += breathe * 0.012f
            headTilt += 7f
            zzz = p
        }
        CatAction.SIGH -> {
            val s = sin(PI_F * p)
            offY += 3f * s
            sqY -= 0.03f * s
            headTilt -= 4f * s
            tear = p
        }
        null -> Unit
    }

    val wagCycles = if (happy) 4f else 1f
    val wagAmp = when {
        happy -> 26f
        sad -> 4f
        else -> 11f
    }
    val tailAngle = sin(phase * TWO_PI_F * wagCycles) * wagAmp + (if (sad) 22f else 0f)
    val eyeBlink = if (eyesClosed || happyEyes) 1f else blink

    // ---- ground shadow (stays on the ground while the cat jumps)
    val shadowScale = 1f - 0.35f * (jumpH / 16f).coerceIn(0f, 1f)
    drawOval(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = Offset((50f - 24f * shadowScale) * u, 109f * u),
        size = Size(48f * shadowScale * u, 6f * u)
    )

    withTransform({
        translate(0f, offY * u)
        scale(sqX, sqY, pivot = Offset(50f * u, 112f * u))
        rotate(rot, pivot = Offset(50f * u, 78f * u))
    }) {
        // tail (behind the body)
        val tailPath = Path().apply {
            moveTo(66f * u, 102f * u)
            cubicTo(90f * u, 104f * u, 98f * u, 82f * u, 88f * u, 70f * u)
        }
        rotate(tailAngle, pivot = Offset(66f * u, 102f * u)) {
            drawPath(tailPath, OUTLINE, style = Stroke(width = 10.5f * u, cap = StrokeCap.Round))
            drawPath(tailPath, FUR, style = Stroke(width = 7.5f * u, cap = StrokeCap.Round))
        }

        // body + belly
        drawOval(FUR, topLeft = Offset(26f * u, 66f * u), size = Size(48f * u, 44f * u))
        drawOval(
            OUTLINE, topLeft = Offset(26f * u, 66f * u), size = Size(48f * u, 44f * u),
            style = Stroke(width = 2.4f * u)
        )
        drawOval(FUR_LIGHT, topLeft = Offset(36f * u, 80f * u), size = Size(28f * u, 26f * u))

        // front paws
        fun paw(cx: Float, lift: Float) {
            val tl = Offset((cx - 7f) * u, (103f - lift) * u)
            drawOval(FUR_LIGHT, topLeft = tl, size = Size(14f * u, 9f * u))
            drawOval(OUTLINE, topLeft = tl, size = Size(14f * u, 9f * u), style = Stroke(width = 1.8f * u))
        }
        paw(41f, pawLift)
        paw(59f, pawLift * 0.5f)

        // head
        rotate(headTilt, pivot = Offset(50f * u, 72f * u)) {
            fun ear(mirror: Boolean, rotDeg: Float) {
                fun px(x: Float) = (if (mirror) 100f - x else x) * u
                val outer = Path().apply {
                    moveTo(px(26f), 44f * u)
                    lineTo(px(27f), 16f * u)
                    lineTo(px(45f), 28f * u)
                    close()
                }
                val inner = Path().apply {
                    moveTo(px(29f), 42f * u)
                    lineTo(px(28.5f), 22f * u)
                    lineTo(px(42f), 31f * u)
                    close()
                }
                rotate(rotDeg, pivot = Offset(px(37f), 36f * u)) {
                    drawPath(outer, FUR)
                    drawPath(outer, OUTLINE, style = Stroke(width = 2.4f * u, join = StrokeJoin.Round))
                    drawPath(inner, EAR_PINK)
                }
            }
            ear(mirror = false, rotDeg = -earDroop + earTwitch)
            ear(mirror = true, rotDeg = earDroop - earTwitch)

            drawCircle(FUR, radius = 26f * u, center = Offset(50f * u, 52f * u))
            drawCircle(OUTLINE, radius = 26f * u, center = Offset(50f * u, 52f * u), style = Stroke(width = 2.4f * u))

            // forehead stripes
            val stripe = FUR_DARK.copy(alpha = 0.8f)
            drawLine(stripe, Offset(50f * u, 28f * u), Offset(50f * u, 35f * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
            drawLine(stripe, Offset(44f * u, 29.5f * u), Offset(45f * u, 35f * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
            drawLine(stripe, Offset(56f * u, 29.5f * u), Offset(55f * u, 35f * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)

            // muzzle + blush
            drawOval(FUR_LIGHT, topLeft = Offset(37f * u, 55f * u), size = Size(26f * u, 17f * u))
            val blush = EAR_PINK.copy(alpha = if (happy) 0.75f else 0.5f)
            drawCircle(blush, radius = 5f * u, center = Offset(31f * u, 60f * u))
            drawCircle(blush, radius = 5f * u, center = Offset(69f * u, 60f * u))

            // eyes
            val eyeY = 50f
            for (side in listOf(-1f, 1f)) {
                val cx = 50f + side * 11f + pupilX
                when {
                    eyesClosed -> drawArc(
                        OUTLINE, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset((cx - 4.5f) * u, (eyeY - 3f) * u), size = Size(9f * u, 6f * u),
                        style = Stroke(width = 1.9f * u, cap = StrokeCap.Round)
                    )
                    happyEyes -> drawArc(
                        OUTLINE, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset((cx - 4.5f) * u, (eyeY - 2f) * u), size = Size(9f * u, 7f * u),
                        style = Stroke(width = 1.9f * u, cap = StrokeCap.Round)
                    )
                    else -> {
                        val rx = if (sad) 5.6f else 4.6f
                        val ry = (if (sad) 6.6f else 5.4f) * eyeBlink
                        drawOval(OUTLINE, topLeft = Offset((cx - rx) * u, (eyeY - ry) * u), size = Size(2f * rx * u, 2f * ry * u))
                        drawCircle(
                            Color.White, radius = (if (sad) 2.1f else 1.6f) * u,
                            center = Offset((cx - 1.4f) * u, (eyeY - 1.8f * eyeBlink) * u)
                        )
                        if (sad) {
                            drawCircle(
                                Color.White.copy(alpha = 0.85f), radius = 1.0f * u,
                                center = Offset((cx + 1.8f) * u, (eyeY + 2.2f * eyeBlink) * u)
                            )
                        }
                    }
                }
            }

            // tear
            if (tear >= 0f) {
                val a = (1f - tear * tear).coerceIn(0f, 1f)
                drawCircle(TEAR.copy(alpha = a), radius = 2.0f * u, center = Offset(35.5f * u, (55f + 24f * tear) * u))
            }

            // nose
            val nose = Path().apply {
                moveTo(47.6f * u, 58.6f * u)
                lineTo(52.4f * u, 58.6f * u)
                lineTo(50f * u, 61.6f * u)
                close()
            }
            drawPath(nose, NOSE)

            // mouth
            val mouthStroke = Stroke(width = 1.5f * u, cap = StrokeCap.Round)
            when {
                mouthOpen > 0.05f -> drawOval(
                    Color(0xFF7A3B4E),
                    topLeft = Offset(46.6f * u, 63f * u),
                    size = Size(6.8f * u, (2f + 5f * mouthOpen) * u)
                )
                happy -> drawArc(
                    OUTLINE, startAngle = 15f, sweepAngle = 150f, useCenter = false,
                    topLeft = Offset(43f * u, 58.5f * u), size = Size(14f * u, 9f * u), style = mouthStroke
                )
                sad -> drawArc(
                    OUTLINE, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(45f * u, 64.5f * u), size = Size(10f * u, 6f * u), style = mouthStroke
                )
                else -> {
                    drawArc(
                        OUTLINE, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(44.5f * u, 61.2f * u), size = Size(5.5f * u, 4.5f * u), style = mouthStroke
                    )
                    drawArc(
                        OUTLINE, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(50f * u, 61.2f * u), size = Size(5.5f * u, 4.5f * u), style = mouthStroke
                    )
                }
            }

            // whiskers
            val whisker = OUTLINE.copy(alpha = 0.55f)
            for (side in listOf(-1f, 1f)) {
                drawLine(whisker, Offset((50f + side * 12f) * u, 61f * u), Offset((50f + side * 28f) * u, 57.5f * u), strokeWidth = 1.1f * u, cap = StrokeCap.Round)
                drawLine(whisker, Offset((50f + side * 12f) * u, 63.5f * u), Offset((50f + side * 29f) * u, 63.5f * u), strokeWidth = 1.1f * u, cap = StrokeCap.Round)
                drawLine(whisker, Offset((50f + side * 12f) * u, 66f * u), Offset((50f + side * 28f) * u, 69.5f * u), strokeWidth = 1.1f * u, cap = StrokeCap.Round)
            }
        }
    }

    // ---- extras drawn outside the body transform
    ballPos?.let { center ->
        drawCircle(BALL, radius = 5.5f * u, center = center)
        drawCircle(Color.White.copy(alpha = 0.7f), radius = 1.6f * u, center = Offset(center.x - 1.8f * u, center.y - 1.8f * u))
    }

    if (zzz >= 0f) {
        for (i in 0..2) {
            val t = (zzz * 2.2f + i * 0.33f) % 1f
            val zx = (66f + i * 6f + 5f * t) * u
            val zy = (30f - 24f * t) * u
            val s = (3.5f + 3.5f * t) * u
            val zPath = Path().apply {
                moveTo(zx, zy)
                lineTo(zx + s, zy)
                lineTo(zx, zy + s)
                lineTo(zx + s, zy + s)
            }
            drawPath(
                zPath, ZZZ.copy(alpha = sin(PI_F * t).coerceIn(0f, 1f)),
                style = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
