package com.consistencygridwallpaper.ui.compose.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.ui.compose.theme.*

data class TourStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val targetIndex: Int
)

@Composable
fun TourOverlay(
    targetRects: Map<Int, Rect>,
    steps: List<TourStep>,
    currentStep: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    if (currentStep >= steps.size) return
    val stepData = steps[currentStep]

    // Pulse Animation for the Border Glow
    val infiniteTransition = rememberInfiniteTransition(label = "borderGlowShared")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    // Bouncing Animation for the Arrow
    val arrowBounce by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounce"
    )

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // Sliding target rect calculation
    val defaultRect = remember(screenWidthPx, screenHeightPx) {
        val cx = screenWidthPx / 2f
        val cy = screenHeightPx / 2f
        Rect(cx - 60f, cy - 60f, cx + 60f, cy + 60f)
    }

    // Using stable, snappy spring physics for fast and precise sliding without overhead
    val slideSpec = remember {
        spring<Float>(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    val lastKnownRects = remember { mutableStateMapOf<Int, Rect>() }
    val targetIndex = stepData.targetIndex
    val measuredRect = targetRects[targetIndex]
    if (measuredRect != null) {
        lastKnownRects[targetIndex] = measuredRect
    }
    val currentRect = measuredRect ?: lastKnownRects[targetIndex] ?: defaultRect

    val animLeft by animateFloatAsState(
        targetValue = currentRect.left,
        animationSpec = slideSpec,
        label = "animLeft"
    )
    val animTop by animateFloatAsState(
        targetValue = currentRect.top,
        animationSpec = slideSpec,
        label = "animTop"
    )
    val animRight by animateFloatAsState(
        targetValue = currentRect.right,
        animationSpec = slideSpec,
        label = "animRight"
    )
    val animBottom by animateFloatAsState(
        targetValue = currentRect.bottom,
        animationSpec = slideSpec,
        label = "animBottom"
    )

    // Card Fade Out/In logic on step change
    val cardAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    var visibleStep by remember { mutableStateOf(currentStep) }

    LaunchedEffect(currentStep) {
        cardAlpha.animateTo(0f, animationSpec = tween(150))
        visibleStep = currentStep
        cardAlpha.animateTo(1f, animationSpec = tween(200))
    }

    // Compute positioning only based on stable visibleStep rect target values
    val visibleTargetIndex = steps[visibleStep].targetIndex
    val visibleMeasuredRect = targetRects[visibleTargetIndex]
    val visibleRect = visibleMeasuredRect ?: lastKnownRects[visibleTargetIndex] ?: defaultRect
    val isAbove = remember(visibleRect, screenHeightPx) {
        val spaceAbove = visibleRect.top
        val spaceBelow = screenHeightPx - visibleRect.bottom
        spaceAbove > spaceBelow
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {} // Eat touch inputs
    ) {
        // Spotlight Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f)
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.78f))

            val pad = 6.dp.toPx()
            val highlightRect = Rect(
                left = animLeft - pad,
                top = animTop - pad,
                right = animRight + pad,
                bottom = animBottom + pad
            )

            // Dynamic flashlight/spotlight diffusion glow
            val center = Offset(highlightRect.left + highlightRect.width / 2f, highlightRect.top + highlightRect.height / 2f)
            val glowRadius = maxOf(highlightRect.width, highlightRect.height) * 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OrangeAccent.copy(alpha = 0.22f * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )
            
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(highlightRect.left, highlightRect.top),
                size = Size(highlightRect.width, highlightRect.height),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            for (i in 1..3) {
                val glowPad = i * 2.dp.toPx()
                drawRoundRect(
                    color = OrangeAccent.copy(alpha = (0.15f / i) * glowPulse),
                    topLeft = Offset(highlightRect.left - glowPad, highlightRect.top - glowPad),
                    size = Size(highlightRect.width + glowPad * 2, highlightRect.height + glowPad * 2),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = (4 * i).dp.toPx())
                )
            }

            drawRoundRect(
                color = OrangeAccent.copy(alpha = glowPulse),
                topLeft = Offset(highlightRect.left, highlightRect.top),
                size = Size(highlightRect.width, highlightRect.height),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Layout Card and Arrow
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(if (isAbove) Alignment.BottomCenter else Alignment.TopCenter)
                .offset {
                    if (isAbove) {
                        val shiftUp = (screenHeightPx - visibleRect.top + 8.dp.toPx()).toInt()
                        IntOffset(0, -shiftUp)
                    } else {
                        val shiftDown = (visibleRect.bottom + 8.dp.toPx()).toInt()
                        IntOffset(0, shiftDown)
                    }
                }
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = cardAlpha.value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isAbove) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropUp,
                    contentDescription = null,
                    tint = OrangeAccent,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { 
                            translationY = -arrowBounce.dp.toPx()
                            val targetCenter = (animLeft + animRight) / 2f
                            val screenCenter = screenWidthPx / 2f
                            val paddingPx = 24.dp.toPx()
                            val halfArrowPx = 18.dp.toPx()
                            val minX = paddingPx + halfArrowPx
                            val maxX = screenWidthPx - paddingPx - halfArrowPx
                            val clampedTargetCenter = targetCenter.coerceIn(minX, maxX)
                            translationX = clampedTargetCenter - screenCenter
                        }
                )
                Spacer(Modifier.height(4.dp))
            }

            SharedTooltipCard(
                step = visibleStep,
                totalSteps = steps.size,
                stepData = steps[visibleStep],
                onNext = onNext,
                onSkip = onSkip
            )

            if (isAbove) {
                Spacer(Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = OrangeAccent,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { 
                            translationY = arrowBounce.dp.toPx()
                            val targetCenter = (animLeft + animRight) / 2f
                            val screenCenter = screenWidthPx / 2f
                            val paddingPx = 24.dp.toPx()
                            val halfArrowPx = 18.dp.toPx()
                            val minX = paddingPx + halfArrowPx
                            val maxX = screenWidthPx - paddingPx - halfArrowPx
                            val clampedTargetCenter = targetCenter.coerceIn(minX, maxX)
                            translationX = clampedTargetCenter - screenCenter
                        }
                )
            }
        }
    }
}

@Composable
private fun ShimmerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "btnShimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val buttonBrush = Brush.linearGradient(
        colors = listOf(
            OrangeAccent,
            Color(0xFFFF7F50),
            Color(0xFFFF4500),
            OrangeAccent
        )
    )

    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = OrangeAccent.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(14.dp))
            .background(buttonBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val x = width * shimmerProgress
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.0f),
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.0f)
                    ),
                    start = Offset(x - 50.dp.toPx(), 0f),
                    end = Offset(x + 50.dp.toPx(), height)
                )
            )
        }

        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}

private fun highlightText(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val words = text.split(" ")
    val highlightTerms = setOf(
        "wallpaper", "studio", "progress", "streaks", "widgets", 
        "reel", "controller", "goals", "actions", "stats", 
        "habit", "heatmap", "schedule", "filters", "reminder", 
        "tasks", "add", "consistency", "completion", "percentage"
    )
    
    words.forEachIndexed { index, word ->
        val cleanWord = word.lowercase().replace(Regex("[^a-z]"), "")
        val isHighlight = highlightTerms.any { cleanWord.contains(it) }
        
        if (isHighlight) {
            builder.pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = OrangeAccent,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            builder.append(word)
            builder.pop()
        } else {
            builder.append(word)
        }
        
        if (index < words.size - 1) {
            builder.append(" ")
        }
    }
    return builder.toAnnotatedString()
}

@Composable
private fun SharedTooltipCard(
    step: Int,
    totalSteps: Int,
    stepData: TourStep,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = OrangeAccent.copy(alpha = 0.3f),
                spotColor = OrangeAccent.copy(alpha = 0.6f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BgCard.copy(alpha = 0.98f),
                        BgBase.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            OrangeAccent.copy(alpha = 0.6f),
                            Color.Transparent,
                            OrangeAccent.copy(alpha = 0.3f)
                        )
                    )
                ),
                RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Progress Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalSteps) { index ->
                        val isActive = index == step
                        val width by animateDpAsState(
                            targetValue = if (isActive) 16.dp else 6.dp,
                            label = "dotWidth"
                        )
                        val color = if (isActive) OrangeAccent else TextMuted.copy(alpha = 0.3f)
                        Box(
                            modifier = Modifier
                                .size(width = width, height = 6.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                // Step Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(OrangeAccent.copy(alpha = 0.12f))
                        .border(0.5.dp, OrangeAccent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "STEP ${step + 1} OF ${totalSteps}",
                        color = OrangeAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Animated content sliding switch
            AnimatedContent(
                targetState = stepData,
                transitionSpec = {
                    (slideInHorizontally { width -> if (targetState.targetIndex > initialState.targetIndex) width else -width } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> if (targetState.targetIndex > initialState.targetIndex) -width else width } + fadeOut())
                        .using(SizeTransform(clip = false))
                },
                label = "tooltipContentSlider"
            ) { data ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Rotating glowing badges orb
                    val infiniteTransition = rememberInfiniteTransition(label = "badgeOrbit")
                    val badgeRotate by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "badgeRotate"
                    )

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(8.dp, CircleShape, spotColor = OrangeAccent.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        OrangeAccent.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                BorderStroke(
                                    2.dp,
                                    Brush.sweepGradient(
                                        colors = listOf(
                                            OrangeAccent,
                                            Color.Transparent,
                                            OrangeAccent.copy(alpha = 0.4f),
                                            OrangeAccent
                                        )
                                    )
                                ),
                                CircleShape
                            )
                            .graphicsLayer { rotationZ = badgeRotate },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BgMuted.copy(alpha = 0.6f))
                                .graphicsLayer { rotationZ = -badgeRotate },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = data.icon,
                                contentDescription = null,
                                tint = OrangeAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Text(
                        text = data.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = highlightText(data.description),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, GlassStroke),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("Skip", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                
                ShimmerButton(
                    text = if (step == totalSteps - 1) "Finish Tour" else "Next Step",
                    onClick = onNext,
                    modifier = Modifier
                        .weight(2f)
                        .height(44.dp)
                )
            }
        }
    }
}
