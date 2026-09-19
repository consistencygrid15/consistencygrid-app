package com.consistencygridwallpaper.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.consistencygridwallpaper.ui.compose.theme.*

/**
 * Premium white card with optional accent color top-border and drop shadow.
 *
 * @param accentColor  Optional — renders a colored top-border and subtle tinted background.
 * @param glowIntensity  0f = no accent, 1f = full accent intensity.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    contentPadding: Dp = 16.dp,
    accentColor: Color? = null,
    glowIntensity: Float = 0.6f,
    content: @Composable BoxScope.() -> Unit
) {
    val borderBrush = if (accentColor != null) {
        Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.4f * glowIntensity),
                GlassStroke
            )
        )
    } else {
        Brush.verticalGradient(colors = listOf(GlassStroke, GlassStroke))
    }

    val fillBrush = if (accentColor != null) {
        Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.04f * glowIntensity),
                BgCard
            )
        )
    } else {
        Brush.verticalGradient(colors = listOf(BgCard, BgCard))
    }

    Box(
        modifier = modifier
            .shadow(
                elevation    = 3.dp,
                shape        = RoundedCornerShape(cornerRadius),
                spotColor    = CardShadowColor,
                ambientColor = CardShadowColor
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(fillBrush)
            .border(1.dp, borderBrush, RoundedCornerShape(cornerRadius))
            .padding(contentPadding),
        content = content
    )
}

/** Simpler card with a solid tinted background — for stat chips. */
@Composable
fun TintCard(
    accentColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    contentPadding: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(accentColor.copy(alpha = 0.1f))
            .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(cornerRadius))
            .padding(contentPadding),
        content = content
    )
}

/** Plain white card with drop shadow — the base card style for the app. */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation    = 4.dp,
                shape        = RoundedCornerShape(cornerRadius),
                spotColor    = CardShadowColor,
                ambientColor = CardShadowColor
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(BgCard)
            .padding(contentPadding),
        content = content
    )
}
