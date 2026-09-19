package com.consistencygridwallpaper.ui.compose.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Backgrounds ─────────────────────────────────────────────────────────────
val BgDeep         = Color(0xFFF0F2F5)   // subtle page tint
val BgBase         = Color(0xFFF8F9FA)   // screen background (off-white)
val BgCard         = Color(0xFFFFFFFF)   // pure-white card surface
val BgElevated     = Color(0xFFFFFFFF)   // elevated sheets / dialogs
val BgCardHover    = Color(0xFFF3F4F6)   // hover state
val BgMuted        = Color(0xFFF5F5F5)   // muted section background

// ─── Glass / Borders ─────────────────────────────────────────────────────────
val GlassStroke    = Color(0xFFE5E7EB)   // subtle light border
val GlassFill      = Color(0x06000000)   // 2% black fill
val GlassBorder    = Color(0xFFE5E7EB)   // card border

// ─── Orange — Primary accent (habits, dashboard, CTAs) ───────────────────────
val OrangeAccent   = Color(0xFFFF7A00)
val OrangeLight    = Color(0xFFFFB347)
val OrangeDark     = Color(0xFFE55A00)
val OrangeMuted    = Color(0xFFFFF3E8)   // very light orange tint for backgrounds
val OrangeDim      = Color(0x28FF7A00)   // 16% orange for bg chips
val OrangeGlow     = Color(0x55FF7A00)

// ─── Violet / Goals ──────────────────────────────────────────────────────────
val VioletAccent   = Color(0xFF6B46C1)
val VioletLight    = Color(0xFFA78BFA)
val VioletDim      = Color(0x286B46C1)
val VioletGlow     = Color(0x556B46C1)
val VioletMuted    = Color(0xFFF3F0FF)   // very light violet background tint

// ─── Green / Streaks / Done ───────────────────────────────────────────────────
val GreenAccent    = Color(0xFF16A34A)
val GreenLight     = Color(0xFF86EFAC)
val GreenDim       = Color(0x2816A34A)
val GreenMuted     = Color(0xFFEFFEF4)   // very light green background tint
val GreenSuccess   = Color(0xFF16A34A)

// ─── Sky / Reminders ─────────────────────────────────────────────────────────
val SkyAccent      = Color(0xFF0284C7)
val SkyLight       = Color(0xFF7DD3FC)
val SkyDim         = Color(0x280284C7)
val SkyMuted       = Color(0xFFEFF8FF)   // very light sky background tint
val BlueInfo       = Color(0xFF2563EB)

// ─── Gold / Premium ───────────────────────────────────────────────────────────
val GoldAccent     = Color(0xFFCA8A04)
val GoldLight      = Color(0xFFFDE68A)
val GoldDim        = Color(0x28CA8A04)

// ─── Rose / Error / Danger ────────────────────────────────────────────────────
val RoseAccent     = Color(0xFFE11D48)
val RoseDim        = Color(0x28E11D48)
val RedError       = Color(0xFFE11D48)
val RedAccent      = Color(0xFFE11D48)

// ─── Text ────────────────────────────────────────────────────────────────────
val TextPrimary    = Color(0xFF111827)   // near-black
val TextSecondary  = Color(0xFF4B5563)   // medium-dark grey
val TextMuted      = Color(0xFF9CA3AF)   // light grey
val TextOnOrange   = Color(0xFFFFFFFF)   // white text on orange buttons

// ─── Shadows ─────────────────────────────────────────────────────────────────
val CardShadowColor = Color(0x14000000)  // 8% black for drop shadows

// ─── Light Color Scheme ────────────────────────────────────────────────────────
private val CgLightColors = lightColorScheme(
    primary          = OrangeAccent,
    onPrimary        = Color.White,
    primaryContainer = OrangeMuted,
    secondary        = VioletAccent,
    onSecondary      = Color.White,
    tertiary         = GreenAccent,
    background       = BgBase,
    surface          = BgCard,
    surfaceVariant   = BgElevated,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    outline          = GlassStroke,
    error            = RoseAccent,
)

// ─── Typography ──────────────────────────────────────────────────────────────
val CgTypography = Typography(
    displayLarge = TextStyle(
        fontWeight    = FontWeight.ExtraBold,
        fontSize      = 56.sp,
        letterSpacing = (-1.5).sp,
        color         = TextPrimary
    ),
    displayMedium = TextStyle(
        fontWeight    = FontWeight.ExtraBold,
        fontSize      = 44.sp,
        letterSpacing = (-1).sp,
        color         = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontWeight    = FontWeight.Bold,
        fontSize      = 28.sp,
        letterSpacing = (-0.5).sp,
        color         = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 22.sp,
        color         = TextPrimary,
    ),
    headlineSmall = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 18.sp,
        color         = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontWeight    = FontWeight.Bold,
        fontSize      = 20.sp,
        color         = TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 16.sp,
        color         = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontWeight    = FontWeight.Normal,
        fontSize      = 16.sp,
        color         = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        color         = TextSecondary,
    ),
    bodySmall = TextStyle(
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        color         = TextMuted,
    ),
    labelLarge = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 14.sp,
        color         = TextPrimary,
    ),
    labelMedium = TextStyle(
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        color         = TextSecondary,
    ),
    labelSmall = TextStyle(
        fontWeight    = FontWeight.Medium,
        fontSize      = 10.sp,
        color         = TextMuted,
        letterSpacing = 0.5.sp
    ),
)

// ─── App Theme Composable ─────────────────────────────────────────────────────
@Composable
fun CgAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CgLightColors,
        typography  = CgTypography,
        content     = content
    )
}
