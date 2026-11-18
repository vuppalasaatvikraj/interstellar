package com.bitchat.android.ui.messenger

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Official Saturn Design System v2 Palette
val SaturnPaper = Color(0xFFFFF9E8)
val SaturnSurface = Color(0xFFFFFCF3)
val SaturnSurfaceSecondary = Color(0xFFFFF4DB)
val SaturnInk = Color(0xFF2B2B2B)
val SaturnSecondaryText = Color(0xFF716B65)
val SaturnYellow = Color(0xFFF7D774)
val SaturnPeach = Color(0xFFF7A8A8)
val SaturnSkyBlue = Color(0xFFB9D8FF)
val SaturnSage = Color(0xFFB3D9B1)
val SaturnLavender = Color(0xFFE8D8FF)
val SaturnSand = Color(0xFFD9C9B8)
val SaturnError = Color(0xFFC65E65)

// 1. MagicUI RippleButton
@Composable
fun SaturnRippleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = SaturnPeach,
    rippleColor: Color = Color(0xFFADD8E6),
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val scope = rememberCoroutineScope()
    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    val rippleRadius = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) containerColor else SaturnSand)
            .border(2.dp, SaturnInk, RoundedCornerShape(18.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    tapOffset = offset
                    scope.launch {
                        rippleRadius.snapTo(0f)
                        rippleAlpha.snapTo(0.55f)
                        launch {
                            rippleRadius.animateTo(
                                targetValue = size.width.toFloat() * 1.3f,
                                animationSpec = tween(420, easing = FastOutSlowInEasing)
                            )
                        }
                        launch {
                            rippleAlpha.animateTo(0f, animationSpec = tween(420))
                        }
                    }
                    onClick()
                }
            }
            .drawBehind {
                if (rippleAlpha.value > 0f) {
                    drawCircle(
                        color = rippleColor.copy(alpha = rippleAlpha.value),
                        radius = rippleRadius.value,
                        center = tapOffset
                    )
                }
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = SaturnInk, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = SaturnInk
            )
        }
    }
}

// 2. MagicUI TypingAnimation (for profile name, username, "Saturn it", etc.)
@Composable
fun SaturnTypingText(
    fullText: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = SaturnInk,
    charDelayMs: Long = 55L,
    repeatLoop: Boolean = false
) {
    var visibleCount by remember(fullText) { mutableIntStateOf(0) }

    LaunchedEffect(fullText, repeatLoop) {
        do {
            visibleCount = 0
            for (i in 1..fullText.length) {
                delay(charDelayMs)
                visibleCount = i
            }
            if (repeatLoop) delay(2200L)
        } while (repeatLoop)
    }

    Text(
        text = fullText.take(visibleCount),
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

// 3. MagicUI AnimatedGridPattern (skewed pulsing grid in Saturn colors)
@Composable
fun SaturnAnimatedGridBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "grid_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "grid_alpha"
    )

    val highlightedCells = remember {
        listOf(
            Pair(1, 2) to SaturnYellow,
            Pair(3, 1) to SaturnPeach,
            Pair(2, 4) to SaturnSkyBlue,
            Pair(4, 3) to SaturnSage,
            Pair(1, 5) to SaturnLavender
        )
    }

    Canvas(modifier = modifier) {
        val cellSize = 38.dp.toPx()
        val cols = (size.width / cellSize).toInt() + 2
        val rows = (size.height / cellSize).toInt() + 2

        for (c in 0..cols) {
            drawLine(
                color = SaturnSand.copy(alpha = 0.35f),
                start = Offset(c * cellSize, 0f),
                end = Offset(c * cellSize, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        for (r in 0..rows) {
            drawLine(
                color = SaturnSand.copy(alpha = 0.35f),
                start = Offset(0f, r * cellSize),
                end = Offset(size.width, r * cellSize),
                strokeWidth = 1.dp.toPx()
            )
        }
        highlightedCells.forEach { (coord, color) ->
            drawRect(
                color = color.copy(alpha = pulse),
                topLeft = Offset(coord.first * cellSize, coord.second * cellSize),
                size = Size(cellSize, cellSize)
            )
        }
    }
}

// 4. MagicUI Meteors + Brand Board Doodles (HomeScreen & ChatScreen background)
@Composable
fun SaturnMeteorsAndDoodlesBackground(
    modifier: Modifier = Modifier,
    meteorCount: Int = 14
) {
    val meteorSeeds = remember {
        val palette = listOf(SaturnYellow, SaturnPeach, SaturnSkyBlue, SaturnLavender, SaturnSage)
        List(meteorCount) { idx ->
            Triple(
                Random(idx * 31).nextFloat(),
                Random(idx * 73).nextFloat(),
                palette[idx % palette.size]
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "saturn_meteors")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meteor_fall"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Pastel falling meteors (no text)
        meteorSeeds.forEachIndexed { idx, (startXFrac, delayFrac, color) ->
            val localT = (progress + delayFrac) % 1f
            val headX = (startXFrac * w * 1.3f) - (localT * w * 0.55f)
            val headY = -40.dp.toPx() + (localT * (h + 120.dp.toPx()))
            val tailLen = 54.dp.toPx()

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(color.copy(alpha = 0.75f), Color.Transparent),
                    start = Offset(headX, headY),
                    end = Offset(headX + tailLen * 0.6f, headY - tailLen)
                ),
                start = Offset(headX, headY),
                end = Offset(headX + tailLen * 0.6f, headY - tailLen),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = color,
                radius = 2.5.dp.toPx(),
                center = Offset(headX, headY)
            )
        }

        // Brand-board doodles: 4-point sparkles, hearts, flower, cloud, mini ringed Saturn
        drawSparkleDoodle(Offset(w * 0.14f, h * 0.22f), 11.dp.toPx(), SaturnYellow)
        drawSparkleDoodle(Offset(w * 0.86f, h * 0.34f), 9.dp.toPx(), SaturnPeach)
        drawSparkleDoodle(Offset(w * 0.78f, h * 0.76f), 12.dp.toPx(), SaturnSkyBlue)

        // Hand-drawn outline heart doodle
        val hx = w * 0.16f
        val hy = h * 0.68f
        val hs = 14.dp.toPx()
        val heartPath = Path().apply {
            moveTo(hx, hy + hs * 0.4f)
            cubicTo(hx - hs, hy - hs * 0.3f, hx - hs * 0.4f, hy - hs, hx, hy - hs * 0.35f)
            cubicTo(hx + hs * 0.4f, hy - hs, hx + hs, hy - hs * 0.3f, hx, hy + hs * 0.4f)
        }
        drawPath(heartPath, color = SaturnInk.copy(alpha = 0.22f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

        // Mini ringed Saturn doodle
        val px = w * 0.84f
        val py = h * 0.56f
        val pr = 12.dp.toPx()
        drawCircle(SaturnYellow.copy(alpha = 0.45f), radius = pr, center = Offset(px, py))
        drawCircle(SaturnInk.copy(alpha = 0.22f), radius = pr, center = Offset(px, py), style = Stroke(1.8.dp.toPx()))
        rotate(degrees = -22f, pivot = Offset(px, py)) {
            drawOval(
                color = SaturnInk.copy(alpha = 0.22f),
                topLeft = Offset(px - pr * 1.8f, py - pr * 0.35f),
                size = Size(pr * 3.6f, pr * 0.7f),
                style = Stroke(1.8.dp.toPx())
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkleDoodle(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
    }
    drawPath(path, color = color.copy(alpha = 0.65f))
    drawPath(path, color = SaturnInk.copy(alpha = 0.25f), style = Stroke(1.3.dp.toPx()))
}

// 5. MagicUI Globe -> Planet Saturn Launch Splash Screen
@Composable
fun SaturnPlanetSplashScreen(
    onFinished: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "saturn_orbit")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "moon_orbit"
    )

    LaunchedEffect(Unit) {
        delay(1750L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SaturnPaper),
        contentAlignment = Alignment.Center
    ) {
        SaturnMeteorsAndDoodlesBackground(modifier = Modifier.fillMaxSize(), meteorCount = 10)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = 46.dp.toPx()

                // Tilted ring behind
                rotate(degrees = -22f, pivot = Offset(cx, cy)) {
                    drawOval(
                        color = SaturnPeach,
                        topLeft = Offset(cx - r * 1.9f, cy - r * 0.42f),
                        size = Size(r * 3.8f, r * 0.84f),
                        style = Stroke(width = 9.dp.toPx())
                    )
                    drawOval(
                        color = SaturnInk,
                        topLeft = Offset(cx - r * 1.9f, cy - r * 0.42f),
                        size = Size(r * 3.8f, r * 0.84f),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                // Planet Saturn body + cute eyes from brand board
                drawCircle(color = SaturnYellow, radius = r, center = Offset(cx, cy))
                drawCircle(color = SaturnInk, radius = r, center = Offset(cx, cy), style = Stroke(3.dp.toPx()))
                drawOval(
                    color = SaturnInk,
                    topLeft = Offset(cx - 11.dp.toPx(), cy - 7.dp.toPx()),
                    size = Size(5.dp.toPx(), 10.dp.toPx())
                )
                drawOval(
                    color = SaturnInk,
                    topLeft = Offset(cx + 5.dp.toPx(), cy - 7.dp.toPx()),
                    size = Size(5.dp.toPx(), 10.dp.toPx())
                )

                // Front arc of tilted ring
                rotate(degrees = -22f, pivot = Offset(cx, cy)) {
                    drawArc(
                        color = SaturnPeach,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - r * 1.9f, cy - r * 0.42f),
                        size = Size(r * 3.8f, r * 0.84f),
                        style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Orbiting little Moon
                val rad = Math.toRadians(orbitAngle.toDouble())
                val mx = cx + (cos(rad) * r * 1.65f).toFloat()
                val my = cy + (sin(rad) * r * 0.65f).toFloat()
                drawCircle(color = SaturnSkyBlue, radius = 8.dp.toPx(), center = Offset(mx, my))
                drawCircle(color = SaturnInk, radius = 8.dp.toPx(), center = Offset(mx, my), style = Stroke(2.dp.toPx()))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "saturn.",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SaturnInk
            )
            Spacer(modifier = Modifier.height(4.dp))
            SaturnTypingText(
                fullText = "A little more human.",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = SaturnSecondaryText,
                charDelayMs = 45L
            )
        }
    }
}

// 6. MagicUI AvatarCircles (Overlapping Moon Contact Circles)
@Composable
fun SaturnAvatarCircles(
    moonNames: List<String>,
    extraCount: Int = 0,
    avatarSize: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val colors = listOf(SaturnYellow, SaturnPeach, SaturnSkyBlue, SaturnSage, SaturnLavender)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-10).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        moonNames.take(5).forEachIndexed { idx, name ->
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(colors[idx % colors.size])
                    .border(2.dp, SaturnInk, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.trim().take(1).uppercase().ifBlank { "M" },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = SaturnInk
                )
            }
        }
        if (extraCount > 0) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(SaturnInk)
                    .border(2.dp, SaturnSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extraCount",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = SaturnSurface
                )
            }
        }
    }
}

// 7. MagicUI Dock (For Users to Communicate With You on About Saturn)
data class SaturnDockLink(
    val label: String,
    val url: String,
    val icon: ImageVector,
    val pillColor: Color
)

@Composable
fun SaturnCreatorDock(
    links: List<SaturnDockLink>,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = SaturnSurface,
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(2.dp, SaturnInk)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            links.forEach { item ->
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val animatedSize by animateDpAsState(
                    targetValue = if (isPressed) 52.dp else 42.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dock_magnify"
                )

                Box(
                    modifier = Modifier
                        .size(animatedSize)
                        .clip(CircleShape)
                        .background(item.pillColor)
                        .border(1.5.dp, SaturnInk, CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onOpenUrl(item.url) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = SaturnInk,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// 8. MagicUI Confetti (Fires at end of Onboarding for New Users)
@Composable
fun SaturnConfettiOverlay(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    if (!trigger) return
    val particles = remember {
        val palette = listOf(SaturnYellow, SaturnPeach, SaturnSkyBlue, SaturnSage, SaturnLavender)
        List(45) { i ->
            Triple(
                Random(i * 17).nextFloat(),
                Random(i * 43).nextFloat() * 0.6f + 0.4f,
                palette[i % palette.size]
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(1600, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEachIndexed { idx, (xFrac, speedMult, color) ->
            val x = xFrac * size.width + sin(progress.value * 8f + idx) * 18.dp.toPx()
            val y = -20.dp.toPx() + progress.value * size.height * 1.15f * speedMult
            drawRoundRect(
                color = color.copy(alpha = (1f - progress.value * 0.7f).coerceIn(0f, 1f)),
                topLeft = Offset(x, y),
                size = Size(8.dp.toPx(), 12.dp.toPx())
            )
        }
    }
}