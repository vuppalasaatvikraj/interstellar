package com.bitchat.android.ui.messenger

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
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

// Dark Mode Tokens derived from Saturn Ink (#2B2B2B) & Sand (#D9C9B8)
val SaturnDarkBg = Color(0xFF2B2B2B)
val SaturnDarkSurface = Color(0xFF363432)
val SaturnDarkSurfaceSecondary = Color(0xFF423F3C)

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

// 2. MagicUI TypingAnimation
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

// 3. MagicUI AnimatedGridPattern
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

// 4. Interactive Saturn Mosaic + Falling Meteors Background (Light & Dark Modes)
@Composable
fun SaturnMeteorsAndDoodlesBackground(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    meteorCount: Int = 14
) {
    val bgColor = if (isDarkTheme) SaturnDarkBg else SaturnPaper
    val baseInk = if (isDarkTheme) SaturnSand.copy(alpha = 0.24f) else SaturnInk.copy(alpha = 0.20f)
    val litPalette = remember(isDarkTheme) {
        if (isDarkTheme) {
            listOf(SaturnYellow, SaturnPeach, SaturnSkyBlue, SaturnLavender, SaturnSage)
        } else {
            listOf(SaturnPeach, SaturnYellow, SaturnSkyBlue, SaturnSage, SaturnLavender)
        }
    }

    // Touch / drag magnet state (ported from your Swing ShapeMosaicPanel)
    var targetX by remember { mutableFloatStateOf(-10_000f) }
    var targetY by remember { mutableFloatStateOf(-10_000f) }
    var easedX by remember { mutableFloatStateOf(-10_000f) }
    var easedY by remember { mutableFloatStateOf(-10_000f) }
    var wantHold by remember { mutableFloatStateOf(0f) }
    var hold by remember { mutableFloatStateOf(0f) }
    var animTime by remember { mutableFloatStateOf(0f) }
    val tau = (PI * 2.0).toFloat()

    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now
                animTime += dt * 0.45f * tau
                easedX += (targetX - easedX) * (1f - exp(-dt * 9f))
                easedY += (targetY - easedY) * (1f - exp(-dt * 9f))
                hold += (wantHold - hold) * (1f - exp(-dt * 5f))
            }
        }
    }

    val meteorSeeds = remember(meteorCount, isDarkTheme) {
        List(meteorCount) { idx ->
            Triple(
                Random(idx * 31 + 7).nextFloat(),
                Random(idx * 73 + 13).nextFloat(),
                litPalette[idx % litPalette.size]
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "saturn_meteors")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meteor_fall"
    )

    Box(
        modifier = modifier
            .background(bgColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.firstOrNull { it.pressed }
                        if (pressed != null) {
                            targetX = pressed.position.x
                            targetY = pressed.position.y
                            if (wantHold == 0f) {
                                easedX = targetX
                                easedY = targetY
                            }
                            wantHold = 1f
                        } else {
                            wantHold = 0f
                            break
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellPx = 44.dp.toPx()
            val baseRadius = cellPx * 0.27f
            val reachPx = 150.dp.toPx()
            val strokePx = 1.6.dp.toPx()

            val cols = ceil(size.width / cellPx).toInt()
            val rows = ceil(size.height / cellPx).toInt()

            // 1. Interactive Saturn Doodle Mosaic (6 brand-board elements)
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val cx = col.toFloat()
                    val cy = row.toFloat()
                    val midX = (cx + 0.5f) * cellPx
                    val midY = (cy + 0.5f) * cellPx

                    val seed = hash2(cx, cy)
                    val kind = min(floor(hash2(cx + 7.3f, cy + 7.3f) * 6f).toInt(), 5)
                    val litTarget = litPalette[(col + row * 3).mod(litPalette.size)]

                    val dist = hypot(midX - easedX, midY - easedY)
                    val nearRaw = 1f - smoothstep(0f, reachPx, dist)
                    val near = nearRaw * nearRaw * hold

                    val heading = if (seed < 0.5f) 1f else -1f
                    val angleDeg = Math.toDegrees(
                        ((animTime * (0.55f + seed * 0.6f) * heading + seed * tau) + near * 0.7f * tau).toDouble()
                    ).toFloat()
                    val r = baseRadius * (1f + near * 0.42f)

                    val activeColor = Color(
                        red = baseInk.red + (litTarget.red - baseInk.red) * near,
                        green = baseInk.green + (litTarget.green - baseInk.green) * near,
                        blue = baseInk.blue + (litTarget.blue - baseInk.blue) * near,
                        alpha = (0.55f + 0.45f * near).coerceIn(0f, 1f)
                    )
                    val fillTint = litTarget.copy(alpha = (0.06f + near * 0.40f).coerceIn(0f, 0.8f))

                    translate(left = midX, top = midY) {
                        rotate(degrees = angleDeg, pivot = Offset.Zero) {
                            drawSaturnDoodleElement(
                                kind = kind,
                                r = r,
                                strokeColor = activeColor,
                                fillColor = fillTint,
                                strokeWidth = strokePx
                            )
                        }
                    }
                }
            }

            // 2. Diagonal Falling Pastel Meteors
            val w = size.width
            val h = size.height
            meteorSeeds.forEach { (startXFrac, delayFrac, color) ->
                val localT = (progress + delayFrac) % 1f
                val headX = (startXFrac * w * 1.35f) - (localT * w * 0.6f)
                val headY = -40.dp.toPx() + (localT * (h + 120.dp.toPx()))
                val tailLen = 56.dp.toPx()
                val tailEnd = Offset(headX + tailLen * 0.6f, headY - tailLen)

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(color.copy(alpha = if (isDarkTheme) 0.85f else 0.78f), Color.Transparent),
                        start = Offset(headX, headY),
                        end = tailEnd
                    ),
                    start = Offset(headX, headY),
                    end = tailEnd,
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = color,
                    radius = 2.6.dp.toPx(),
                    center = Offset(headX, headY)
                )
            }
        }
    }
}

private fun DrawScope.drawSaturnDoodleElement(
    kind: Int,
    r: Float,
    strokeColor: Color,
    fillColor: Color,
    strokeWidth: Float
) {
    val strokeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        0 -> { // Ringed Saturn Planet
            drawCircle(fillColor, radius = r * 0.66f, center = Offset.Zero)
            drawCircle(strokeColor, radius = r * 0.66f, center = Offset.Zero, style = strokeStyle)
            rotate(-24f, Offset.Zero) {
                drawOval(
                    color = strokeColor,
                    topLeft = Offset(-r * 1.32f, -r * 0.28f),
                    size = Size(r * 2.64f, r * 0.56f),
                    style = strokeStyle
                )
            }
        }
        1 -> { // 4-Point Sparkle
            val p = Path().apply {
                moveTo(0f, -r)
                quadraticTo(0f, 0f, r, 0f)
                quadraticTo(0f, 0f, 0f, r)
                quadraticTo(0f, 0f, -r, 0f)
                quadraticTo(0f, 0f, 0f, -r)
                close()
            }
            drawPath(p, fillColor)
            drawPath(p, strokeColor, style = strokeStyle)
        }
        2 -> { // Puffy 5-Point Star
            val p = Path()
            for (i in 0 until 10) {
                val rad = if (i % 2 == 0) r * 1.05f else r * 0.52f
                val a = -PI / 2.0 + i * (PI / 5.0)
                val px = (cos(a) * rad).toFloat()
                val py = (sin(a) * rad).toFloat()
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
            drawPath(p, fillColor)
            drawPath(p, strokeColor, style = strokeStyle)
        }
        3 -> { // Heart Doodle
            val s = r * 0.95f
            val p = Path().apply {
                moveTo(0f, s * 0.72f)
                cubicTo(-s * 1.25f, -s * 0.05f, -s * 0.65f, -s * 0.95f, 0f, -s * 0.32f)
                cubicTo(s * 0.65f, -s * 0.95f, s * 1.25f, -s * 0.05f, 0f, s * 0.72f)
                close()
            }
            drawPath(p, fillColor)
            drawPath(p, strokeColor, style = strokeStyle)
        }
        4 -> { // 5-Petal Flower Doodle
            for (i in 0 until 5) {
                val a = i * (2.0 * PI / 5.0)
                val c = Offset((cos(a) * r * 0.52f).toFloat(), (sin(a) * r * 0.52f).toFloat())
                drawCircle(fillColor, radius = r * 0.40f, center = c)
                drawCircle(strokeColor, radius = r * 0.40f, center = c, style = strokeStyle)
            }
            drawCircle(strokeColor, radius = r * 0.25f, center = Offset.Zero, style = strokeStyle)
        }
        else -> { // Fluffy Cloud Doodle
            val p = Path().apply {
                moveTo(-r * 0.75f, r * 0.35f)
                cubicTo(-r * 1.15f, r * 0.35f, -r * 1.1f, -r * 0.25f, -r * 0.55f, -r * 0.25f)
                cubicTo(-r * 0.45f, -r * 0.75f, r * 0.25f, -r * 0.75f, r * 0.42f, -r * 0.25f)
                cubicTo(r * 0.98f, -r * 0.25f, r * 1.05f, r * 0.35f, r * 0.68f, r * 0.35f)
                close()
            }
            drawPath(p, fillColor)
            drawPath(p, strokeColor, style = strokeStyle)
        }
    }
}

private fun hash2(x: Float, y: Float): Float {
    val s = sin(x * 127.1f + y * 311.7f) * 43758.5453123f
    return s - floor(s)
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
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
        SaturnMeteorsAndDoodlesBackground(
            modifier = Modifier.fillMaxSize(),
            isDarkTheme = false,
            meteorCount = 10
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = 46.dp.toPx()

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

// 6. MagicUI AvatarCircles
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

// 7. MagicUI Dock
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

// 8. MagicUI Confetti
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