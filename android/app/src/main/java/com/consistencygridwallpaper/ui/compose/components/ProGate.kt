package com.consistencygridwallpaper.ui.compose.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.ui.compose.theme.GoldAccent
import com.consistencygridwallpaper.ui.compose.theme.GoldLight
import com.consistencygridwallpaper.ui.compose.theme.OrangeAccent

private val GoldPremium = Color(0xFFFFD700)

// ─────────────────────────────────────────────────────────────────────────────
// ProGate — Conditionally shows content or a lock overlay
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps a composable and conditionally either renders it (when Pro)
 * or shows a lock overlay (when Free).
 *
 * Usage:
 * ```kotlin
 * ProGate(isPro = isPro, feature = "Life in Weeks", onUpgrade = { navController.navigate("subscription") }) {
 *     LifeInWeeksView()
 * }
 * ```
 *
 * @param isPro      Whether the user has an active Pro subscription
 * @param feature    Display name for the locked feature (shown in overlay)
 * @param onUpgrade  Called when user taps the "Upgrade to Pro" button
 * @param style      How the lock is displayed — [ProGateStyle.OVERLAY] or [ProGateStyle.CHIP]
 * @param content    The Pro-only content to render when unlocked
 */
@Composable
fun ProGate(
    isPro: Boolean,
    feature: String,
    onUpgrade: () -> Unit,
    style: ProGateStyle = ProGateStyle.OVERLAY,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isPro) {
        Box(modifier = modifier) {
            content()
        }
    } else {
        when (style) {
            ProGateStyle.OVERLAY -> LockedOverlay(feature, onUpgrade, modifier, content)
            ProGateStyle.CHIP    -> LockedChip(feature, onUpgrade, modifier)
            ProGateStyle.BADGE   -> LockedBadgeWrapper(onUpgrade, modifier, content)
        }
    }
}

enum class ProGateStyle {
    /** Blurred content + centered lock icon + "Upgrade to Pro" button */
    OVERLAY,
    /** Inline chip with a lock icon — for small items like layout options */
    CHIP,
    /** Content visible but with a gold PRO badge overlay in top-right corner */
    BADGE
}

// ─────────────────────────────────────────────────────────────────────────────
// OVERLAY style — Blurred content + lock overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LockedOverlay(
    feature: String,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Render content blurred underneath
        Box(modifier = Modifier.blur(6.dp)) {
            content()
        }

        // Dark scrim over blurred content
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFF0D0D12).copy(0.7f))
        )

        // Lock overlay card
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Animated lock icon
            AnimatedLockIcon()

            Text(
                feature,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                "Available in Pro",
                color = Color.White.copy(0.55f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // Upgrade button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFE5B800), GoldPremium))
                    )
                    .clickable { onUpgrade() }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.WorkspacePremium,
                        null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Upgrade to Pro",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedLockIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "lock")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue  = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_glow"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(GoldPremium.copy(glow * 0.25f), Color.Transparent)
                )
            )
            .border(1.5.dp, GoldPremium.copy(glow * 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = GoldPremium,
            modifier = Modifier.size(30.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CHIP style — Compact lock for inline items (e.g. layout chips, theme cards)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LockedChip(
    feature: String,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1A24))
            .border(1.dp, GoldPremium.copy(0.4f), RoundedCornerShape(10.dp))
            .clickable { onUpgrade() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Rounded.Lock, null, tint = GoldPremium, modifier = Modifier.size(12.dp))
        Text(feature, color = GoldPremium, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BADGE style — Content visible + small PRO badge in corner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LockedBadgeWrapper(
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.clickable { onUpgrade() }) {
        content()

        // Gold PRO badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFFE5B800), GoldPremium))
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("PRO", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
