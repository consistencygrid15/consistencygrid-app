package com.consistencygridwallpaper.ui.compose.tour

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex    
import com.consistencygridwallpaper.ui.compose.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  Registry — write-only from screens, read from overlay
// ─────────────────────────────────────────────────────────────────────────────

val TourTargetRegistry = mutableStateMapOf<String, Rect>()

/**
 * Attach to any composable that should be spotlighted during the tour.
 * Writes boundsInRoot coordinates to [TourTargetRegistry] every layout pass.
 * Never auto-removes — registry entries are overwritten in place, never deleted.
 */
fun Modifier.tourTarget(key: String): Modifier =
    this.onGloballyPositioned { coords ->
        if (coords.isAttached) {
            val b = coords.boundsInRoot()
            // Only write if bounds are non-empty (avoids writing 0-size entries
            // during the first layout pass before the composable has real size)
            if (b.width > 0f && b.height > 0f) {
                TourTargetRegistry[key] = b
            }
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
//  FeatureTourOverlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FeatureTourOverlay(
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    if (!TourManager.isActive) return
    val step = TourManager.currentStep ?: return

    val density = LocalDensity.current

    // Measure the real screen size from the root view (respects edge-to-edge)
    val view = LocalView.current
    val screenWidthPx  = view.rootView.width.toFloat().let { if (it > 0f) it else view.width.toFloat() }
    val screenHeightPx = view.rootView.height.toFloat().let { if (it > 0f) it else view.height.toFloat() }

    // ── Look up target bounds ─────────────────────────────────────────────────
    val targetBounds = TourTargetRegistry[step.targetKey]
    val isTargetReady = targetBounds != null && targetBounds.width > 0f && targetBounds.height > 0f

    // Hold last known bounds so the spotlight doesn't snap to center when
    // navigating between screens (it will be invisible during the transition anyway)
    var lastKnownBounds by remember { mutableStateOf<Rect?>(null) }
    if (isTargetReady) lastKnownBounds = targetBounds

    val resolvedBounds = (if (isTargetReady) targetBounds else lastKnownBounds)
        ?: Rect(screenWidthPx * 0.3f, screenHeightPx * 0.4f, screenWidthPx * 0.7f, screenHeightPx * 0.6f)

    // ── Pulse & animation values ──────────────────────────────────────────────
    val infinite = rememberInfiniteTransition(label = "tourInfinite")
    val glowPulse by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )
    val arrowBounce by infinite.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "arrow"
    )

    // Spotlight alpha: fade out when no target, fade in when ready
    val spotAlpha by animateFloatAsState(
        targetValue   = if (isTargetReady) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "spotAlpha"
    )

    // Animated spotlight rect — smooth spring slide between steps
    val springSpec = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
    val sl by animateFloatAsState(resolvedBounds.left,   springSpec, label = "sl")
    val st by animateFloatAsState(resolvedBounds.top,    springSpec, label = "st")
    val sr by animateFloatAsState(resolvedBounds.right,  springSpec, label = "sr")
    val sb by animateFloatAsState(resolvedBounds.bottom, springSpec, label = "sb")

    // ── Card fade on step change ──────────────────────────────────────────────
    val cardAlpha = remember { Animatable(1f) }
    var displayedStep by remember { mutableStateOf(TourManager.currentStepIndex) }
    LaunchedEffect(TourManager.currentStepIndex) {
        cardAlpha.animateTo(0f, tween(120))
        displayedStep = TourManager.currentStepIndex
        cardAlpha.animateTo(1f, tween(200))
    }
    val displayedStepData = ConsistencyGridTourSteps.getOrNull(displayedStep) ?: step

    // ── Card side: above or below the spotlight ───────────────────────────────
    // Use raw (non-animated) resolved bounds so placement is stable
    val isAbove = (resolvedBounds.top) > (screenHeightPx - resolvedBounds.bottom)

    val overallAlpha = cardAlpha.value * if (isTargetReady) 1f else 0f

    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = {}
            )
    ) {

        // ── 1. Scrim + Spotlight cutout Canvas ────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f)  // required for BlendMode.Clear
        ) {
            val pad = with(density) { 10.dp.toPx() }

            // Full dark scrim
            drawRect(Color.Black.copy(alpha = 0.76f))

            if (spotAlpha > 0.01f) {
                val cx = (sl + sr) / 2f
                val cy = (st + sb) / 2f
                val w  = sr - sl
                val h  = sb - st
                val cr = with(density) { 14.dp.toPx() }

                val hl = Rect(cx - w / 2f - pad, cy - h / 2f - pad, cx + w / 2f + pad, cy + h / 2f + pad)

                // Ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(OrangeAccent.copy(alpha = 0.18f * glowPulse * spotAlpha), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = maxOf(hl.width, hl.height) * 2f
                    ),
                    radius = maxOf(hl.width, hl.height) * 2f,
                    center = Offset(cx, cy)
                )

                // Cutout
                when (step.shape) {
                    SpotlightShape.CIRCLE -> drawCircle(
                        color     = Color.Transparent,
                        center    = Offset(cx, cy),
                        radius    = maxOf(hl.width, hl.height) / 2f,
                        blendMode = BlendMode.Clear
                    )
                    SpotlightShape.ROUNDED_RECT -> drawRoundRect(
                        color        = Color.Transparent,
                        topLeft      = Offset(hl.left, hl.top),
                        size         = Size(hl.width, hl.height),
                        cornerRadius = CornerRadius(cr, cr),
                        blendMode    = BlendMode.Clear
                    )
                }

                // Pulsing rings
                for (i in 1..3) {
                    val gp = i * with(density) { 3.dp.toPx() }
                    val ra = (0.22f / i) * glowPulse * spotAlpha
                    when (step.shape) {
                        SpotlightShape.CIRCLE -> drawCircle(
                            color  = OrangeAccent.copy(alpha = ra),
                            center = Offset(cx, cy),
                            radius = maxOf(hl.width, hl.height) / 2f + gp,
                            style  = Stroke((i * 2.5f).dp.toPx())
                        )
                        SpotlightShape.ROUNDED_RECT -> drawRoundRect(
                            color        = OrangeAccent.copy(alpha = ra),
                            topLeft      = Offset(hl.left - gp, hl.top - gp),
                            size         = Size(hl.width + gp * 2f, hl.height + gp * 2f),
                            cornerRadius = CornerRadius(cr + gp, cr + gp),
                            style        = Stroke((i * 2.5f).dp.toPx())
                        )
                    }
                }

                // Sharp border
                when (step.shape) {
                    SpotlightShape.CIRCLE -> drawCircle(
                        color  = OrangeAccent.copy(alpha = glowPulse * spotAlpha),
                        center = Offset(cx, cy),
                        radius = maxOf(hl.width, hl.height) / 2f,
                        style  = Stroke(with(density) { 2.dp.toPx() })
                    )
                    SpotlightShape.ROUNDED_RECT -> drawRoundRect(
                        color        = OrangeAccent.copy(alpha = glowPulse * spotAlpha),
                        topLeft      = Offset(hl.left, hl.top),
                        size         = Size(hl.width, hl.height),
                        cornerRadius = CornerRadius(cr, cr),
                        style        = Stroke(with(density) { 2.dp.toPx() })
                    )
                }
            }
        }

        // ── 2. Tooltip card (placed relative to spotlight) ────────────────────
        if (overallAlpha > 0.01f) {
            val targetCxDp = with(density) { ((sl + sr) / 2f).toDp() }
            val tooltipTopOffsetPx: Float
            val tooltipAlign: Alignment

            if (isAbove) {
                // Card sits above the spotlight
                tooltipTopOffsetPx = st - with(density) { 8.dp.toPx() }
                tooltipAlign = Alignment.TopCenter
            } else {
                // Card sits below the spotlight
                tooltipTopOffsetPx = sb + with(density) { 14.dp.toPx() }
                tooltipAlign = Alignment.TopCenter
            }

            val tooltipTopDp = with(density) { tooltipTopOffsetPx.toDp() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = overallAlpha }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isAbove) {
                                // anchor bottom of column to tooltipTopOffsetPx
                                Modifier.absoluteOffset(y = with(density) { (tooltipTopOffsetPx - screenHeightPx).toDp() })
                                    .align(Alignment.BottomCenter)
                            } else {
                                Modifier.absoluteOffset(y = tooltipTopDp)
                                    .align(Alignment.TopCenter)
                            }
                        )
                        .padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Arrow pointing up (card is below spotlight)
                    if (!isAbove) {
                        Canvas(
                            modifier = Modifier
                                .width(28.dp)
                                .height(13.dp)
                                .offset(y = with(density) { arrowBounce.toDp() })
                        ) {
                            val path = Path().apply {
                                moveTo(size.width / 2f, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(path, color = BgCard)
                            drawPath(Path().apply {
                                moveTo(0f, size.height)
                                lineTo(size.width / 2f, 0f)
                                lineTo(size.width, size.height)
                            }, color = GlassStroke, style = Stroke(width = with(density) { 1.dp.toPx() }))
                        }
                    }

                    TourTooltipCard(
                        stepData   = displayedStepData,
                        stepIndex  = displayedStep,
                        totalSteps = TourManager.totalSteps,
                        onNext     = onNext,
                        onSkip     = onSkip
                    )

                    // Arrow pointing down (card is above spotlight)
                    if (isAbove) {
                        Canvas(
                            modifier = Modifier
                                .width(28.dp)
                                .height(13.dp)
                                .offset(y = with(density) { (-arrowBounce).toDp() })
                        ) {
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                            drawPath(path, color = BgCard)
                            drawPath(Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width / 2f, size.height)
                                lineTo(size.width, 0f)
                            }, color = GlassStroke, style = Stroke(width = with(density) { 1.dp.toPx() }))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tooltip card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TourTooltipCard(
    stepData:   TourStep,
    stepIndex:  Int,
    totalSteps: Int,
    onNext:     () -> Unit,
    onSkip:     () -> Unit
) {
    Surface(
        shape           = RoundedCornerShape(22.dp),
        color           = BgCard,
        shadowElevation = 14.dp,
        tonalElevation  = 0.dp,
        modifier        = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                HolographicOrb(stepData)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OrangeMuted)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text          = "STEP ${stepIndex + 1} OF $totalSteps",
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = OrangeAccent,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Progress dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                repeat(totalSteps) { i ->
                    val active = i == stepIndex
                    val done   = i < stepIndex
                    val w by animateDpAsState(
                        targetValue   = if (active) 22.dp else 6.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label         = "dot$i"
                    )
                    Box(
                        modifier = Modifier
                            .height(5.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(
                                when {
                                    active -> OrangeAccent
                                    done   -> OrangeAccent.copy(alpha = 0.45f)
                                    else   -> TextMuted.copy(alpha = 0.2f)
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text       = stepData.title,
                fontSize   = 19.sp,
                fontWeight = FontWeight.Black,
                color      = TextPrimary,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text       = stepData.description,
                fontSize   = 13.5.sp,
                color      = TextSecondary,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSkip) {
                    Text("Skip Tour", fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                }
                ShimmerNextButton(
                    label   = if (stepIndex == totalSteps - 1) "Finish 🎉" else "Next Step →",
                    onClick = onNext
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Holographic Orb
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HolographicOrb(step: TourStep) {
    val infinite = rememberInfiniteTransition(label = "orbSpin")
    val rotation by infinite.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label         = "rot"
    )

    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
        ) {
            drawCircle(
                brush  = Brush.sweepGradient(
                    colors = listOf(OrangeAccent.copy(alpha = 0.9f), OrangeLight.copy(alpha = 0.3f), OrangeAccent.copy(alpha = 0.9f)),
                    center = center
                ),
                style  = Stroke(width = 2.dp.toPx()),
                radius = size.minDimension / 2f - 1.dp.toPx()
            )
        }
        Box(
            modifier         = Modifier.size(46.dp).clip(CircleShape).background(OrangeMuted),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = step.icon, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(22.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shimmer Next button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShimmerNextButton(label: String, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val shimX by infinite.animateFloat(
        initialValue  = -300f,
        targetValue   = 300f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label         = "shimX"
    )
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(OrangeDark, OrangeAccent, OrangeLight, OrangeAccent, OrangeDark),
                    start  = Offset(shimX, 0f),
                    end    = Offset(shimX + 300f, 42f)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            fontSize   = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = Color.White,
            textAlign  = TextAlign.Center
        )
    }
}
