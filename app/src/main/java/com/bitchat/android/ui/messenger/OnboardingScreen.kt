package com.bitchat.android.ui.messenger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SaturnOnboardingStep(val index: Int) {
    IDENTITY(0),
    NEARBY_PERMISSIONS(1),
    LOCATION_SWITCH(2),
    BATTERY_AWAKE(3)
}

@Composable
fun SaturnOnboardingScreen(
    currentStep: SaturnOnboardingStep,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    isLoading: Boolean = false,
    onPrimaryAction: () -> Unit,
    onSkipStep: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var showConfetti by remember { mutableStateOf(false) }

    val handlePrimaryClick: () -> Unit = {
        if (currentStep == SaturnOnboardingStep.BATTERY_AWAKE) {
            scope.launch {
                showConfetti = true
                delay(950L)
                onPrimaryAction()
            }
        } else {
            onPrimaryAction()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SaturnPaper
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundDoodles(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StepDotsIndicator(
                    totalSteps = 4,
                    currentIndex = currentStep.index
                )

                Spacer(modifier = Modifier.height(28.dp))

                val titleText = when (currentStep) {
                    SaturnOnboardingStep.IDENTITY -> "Hey, you're home."
                    SaturnOnboardingStep.NEARBY_PERMISSIONS -> "Unlock your offline radar."
                    SaturnOnboardingStep.LOCATION_SWITCH -> "Flip Android's radar switch."
                    SaturnOnboardingStep.BATTERY_AWAKE -> "Keep Saturn awake for you."
                }
                SlideLeftCharacterText(
                    text = titleText,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                FriendlyHighlightedCopy(step = currentStep)

                Spacer(modifier = Modifier.weight(1f))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    label = "mascot_step"
                ) { step ->
                    SaturnMascotStage(step = step)
                }

                Spacer(modifier = Modifier.weight(1f))

                if (currentStep == SaturnOnboardingStep.IDENTITY) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = onNicknameChange,
                        placeholder = {
                            Text("Pick a display name...", color = SaturnSecondaryText)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SaturnSurface,
                            unfocusedContainerColor = SaturnSurface,
                            focusedBorderColor = SaturnPeach,
                            unfocusedBorderColor = SaturnSand,
                            focusedTextColor = SaturnInk,
                            unfocusedTextColor = SaturnInk,
                            cursorColor = SaturnInk
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val ctaLabel = when (currentStep) {
                    SaturnOnboardingStep.IDENTITY -> "Continue"
                    SaturnOnboardingStep.NEARBY_PERMISSIONS -> "Allow Nearby Radar"
                    SaturnOnboardingStep.LOCATION_SWITCH -> "Turn On Location"
                    SaturnOnboardingStep.BATTERY_AWAKE -> "Get Started"
                }
                val isCtaEnabled = !isLoading && (
                    currentStep != SaturnOnboardingStep.IDENTITY || nickname.trim().isNotEmpty()
                )

                SaturnShimmerButton(
                    text = if (isLoading) "Just a little moment..." else ctaLabel,
                    enabled = isCtaEnabled,
                    onClick = handlePrimaryClick,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (onSkipStep != null && currentStep != SaturnOnboardingStep.IDENTITY) {
                        SaturnInteractiveNextButton(
                            text = "next",
                            onClick = onSkipStep
                        )
                    }
                }
            }

            SaturnConfettiOverlay(trigger = showConfetti)
        }
    }
}

@Composable
fun SlideLeftCharacterText(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            text.forEachIndexed { index, char ->
                val offsetX = remember(text, index) { Animatable(24f) }
                val alpha = remember(text, index) { Animatable(0f) }

                LaunchedEffect(text, index) {
                    delay(index * 18L)
                    offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                LaunchedEffect(text, index) {
                    delay(index * 18L)
                    alpha.animateTo(1f, animationSpec = tween(220))
                }

                Text(
                    text = char.toString(),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SaturnInk,
                    modifier = Modifier.graphicsLayer {
                        translationX = offsetX.value
                        this.alpha = alpha.value
                    }
                )
            }
        }
    }
}

@Composable
private fun FriendlyHighlightedCopy(step: SaturnOnboardingStep) {
    when (step) {
        SaturnOnboardingStep.IDENTITY -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Your ", fontSize = 15.sp, color = SaturnSecondaryText)
                HighlighterSpan(
                    text = "private keys",
                    color = SaturnYellow,
                    isUnderline = true
                )
                Text(" generate silently on-device.", fontSize = 15.sp, color = SaturnSecondaryText)
            }
        }
        SaturnOnboardingStep.NEARBY_PERMISSIONS -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lets Saturn find friends via ", fontSize = 15.sp, color = SaturnSecondaryText)
                    HighlighterSpan(
                        text = "Bluetooth & Wi-Fi",
                        color = SaturnSkyBlue,
                        isUnderline = false
                    )
                }
                Text(
                    "even when you have zero internet.",
                    fontSize = 15.sp,
                    color = SaturnSecondaryText
                )
            }
        }
        SaturnOnboardingStep.LOCATION_SWITCH -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Android needs ", fontSize = 15.sp, color = SaturnSecondaryText)
                    HighlighterSpan(
                        text = "Location ON",
                        color = SaturnYellow,
                        isUnderline = true
                    )
                    Text(" to scan Bluetooth.", fontSize = 15.sp, color = SaturnSecondaryText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saturn ", fontSize = 15.sp, color = SaturnSecondaryText)
                    HighlighterSpan(
                        text = "never tracks",
                        color = SaturnSage,
                        isUnderline = false
                    )
                    Text(" where you are.", fontSize = 15.sp, color = SaturnSecondaryText)
                }
            }
        }
        SaturnOnboardingStep.BATTERY_AWAKE -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stops Android from ", fontSize = 15.sp, color = SaturnSecondaryText)
                    HighlighterSpan(
                        text = "pausing your radar",
                        color = SaturnSkyBlue,
                        isUnderline = false
                    )
                }
                Text(
                    "so messages arrive even in your pocket.",
                    fontSize = 15.sp,
                    color = SaturnSecondaryText
                )
            }
        }
    }
}

@Composable
private fun HighlighterSpan(
    text: String,
    color: Color,
    isUnderline: Boolean
) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = SaturnInk,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .drawBehind {
                if (isUnderline) {
                    drawLine(
                        color = color,
                        start = Offset(0f, size.height - 2.dp.toPx()),
                        end = Offset(size.width, size.height - 2.dp.toPx()),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    drawRoundRect(
                        color = color.copy(alpha = 0.65f),
                        topLeft = Offset(-2.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width + 4.dp.toPx(), size.height - 1.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
    )
}

@Composable
fun SaturnShimmerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    val containerColor = if (enabled) SaturnPeach else SaturnSand

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(containerColor)
            .border(2.dp, SaturnInk, RoundedCornerShape(100.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                if (enabled) {
                    val shimmerWidth = size.width * 0.45f
                    val startX = shimmerProgress * size.width
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.45f),
                                Color.Transparent
                            ),
                            start = Offset(startX, 0f),
                            end = Offset(startX + shimmerWidth, size.height)
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = SaturnInk
        )
    }
}

@Composable
fun SaturnInteractiveNextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val arrowOffset by animateFloatAsState(
        targetValue = if (isPressed) 6f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "arrow_nudge"
    )
    val pillBg by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(180),
        label = "pill_fill"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(SaturnYellow.copy(alpha = pillBg * 0.6f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = SaturnInk
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Next",
            tint = SaturnInk,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { translationX = arrowOffset }
        )
    }
}


@Composable
private fun StepDotsIndicator(totalSteps: Int, currentIndex: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { idx ->
            val isActive = idx == currentIndex
            val isDone = idx < currentIndex
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (isActive) 22.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isActive -> SaturnInk
                            isDone -> SaturnSage
                            else -> SaturnSand
                        }
                    )
            )
        }
    }
}

@Composable
private fun SaturnMascotStage(step: SaturnOnboardingStep) {
    val badgeColor = when (step) {
        SaturnOnboardingStep.IDENTITY -> SaturnYellow
        SaturnOnboardingStep.NEARBY_PERMISSIONS -> SaturnSkyBlue
        SaturnOnboardingStep.LOCATION_SWITCH -> SaturnPeach
        SaturnOnboardingStep.BATTERY_AWAKE -> SaturnSage
    }
    val caption = when (step) {
        SaturnOnboardingStep.IDENTITY -> "No phone number needed"
        SaturnOnboardingStep.NEARBY_PERMISSIONS -> "Peer-to-peer radar ready"
        SaturnOnboardingStep.LOCATION_SWITCH -> "Required by Android"
        SaturnOnboardingStep.BATTERY_AWAKE -> "You've got this!"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(176.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(SaturnSurface)
                .border(2.dp, SaturnInk, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(110.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension * 0.36f
                drawCircle(color = badgeColor, radius = r, center = Offset(cx, cy))
                drawCircle(
                    color = SaturnInk,
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(SaturnInk, radius = 4.dp.toPx(), center = Offset(cx - 14.dp.toPx(), cy - 4.dp.toPx()))
                drawCircle(SaturnInk, radius = 4.dp.toPx(), center = Offset(cx + 14.dp.toPx(), cy - 4.dp.toPx()))
                drawCircle(SaturnPeach, radius = 6.dp.toPx(), center = Offset(cx - 22.dp.toPx(), cy + 6.dp.toPx()))
                drawCircle(SaturnPeach, radius = 6.dp.toPx(), center = Offset(cx + 22.dp.toPx(), cy + 6.dp.toPx()))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            color = SaturnSurface,
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.5.dp, SaturnSand)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = SaturnInk,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = caption,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SaturnInk,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BackgroundDoodles(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = SaturnPeach.copy(alpha = 0.25f),
            radius = 64.dp.toPx(),
            center = Offset(size.width * 0.92f, size.height * 0.08f),
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = SaturnSage.copy(alpha = 0.30f),
            radius = 80.dp.toPx(),
            center = Offset(size.width * 0.08f, size.height * 0.92f),
            style = Stroke(width = 3.dp.toPx())
        )
        val sx = size.width * 0.14f
        val sy = size.height * 0.34f
        val sr = 10.dp.toPx()
        val starPath = Path().apply {
            moveTo(sx, sy - sr)
            lineTo(sx, sy + sr)
            moveTo(sx - sr, sy)
            lineTo(sx + sr, sy)
        }
        drawPath(starPath, color = SaturnYellow, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun SaturnOnboardingPreview() {
    var step by remember { mutableStateOf(SaturnOnboardingStep.IDENTITY) }
    var name by remember { mutableStateOf("Saatvik") }
    SaturnOnboardingScreen(
        currentStep = step,
        nickname = name,
        onNicknameChange = { name = it },
        onPrimaryAction = {
            val next = (step.index + 1) % SaturnOnboardingStep.entries.size
            step = SaturnOnboardingStep.entries[next]
        },
        onSkipStep = {
            val next = (step.index + 1) % SaturnOnboardingStep.entries.size
            step = SaturnOnboardingStep.entries[next]
        }
    )
}