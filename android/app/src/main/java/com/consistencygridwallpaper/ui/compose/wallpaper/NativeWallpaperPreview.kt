package com.consistencygridwallpaper.ui.compose.wallpaper

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.ui.compose.theme.OrangeAccent
import com.consistencygridwallpaper.ui.compose.theme.TextMuted
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.foundation.shape.CircleShape

/**
 * Premium native wallpaper preview.
 *
 * Renders [bitmap] inside a phone-shaped frame. While [isRendering] is true a
 * pulsing shimmer is shown. When [isApplying] is true an animated orange glow
 * border pulses around the frame.
 *
 * No WebView — the bitmap is produced directly by [WallpaperCanvasEngine].
 */
@Composable
fun NativeWallpaperPreview(
    bitmap: android.graphics.Bitmap?,
    isRendering: Boolean,
    wallpaperType: String = "lockscreen",
    modifier: Modifier = Modifier
) {
    val frameShape  = RoundedCornerShape(28.dp)

    // Pulsing glow alpha
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.4f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // ── Outer glow ──────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val strokePx = 8.dp.toPx()
                    drawRoundRect(
                        color       = OrangeAccent.copy(alpha = glowAlpha * 0.6f),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style       = Stroke(width = strokePx * 3),
                        topLeft     = androidx.compose.ui.geometry.Offset(-strokePx, -strokePx),
                        size        = size.copy(
                            width  = size.width  + strokePx * 2,
                            height = size.height + strokePx * 2
                        )
                    )
                }
        )

        // ── Phone frame ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(frameShape)
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(listOf(
                        OrangeAccent.copy(glowAlpha),
                        OrangeAccent.copy(glowAlpha * 0.4f)
                    )),
                    shape = frameShape
                )
                .background(Color(0xFF09090B))
        ) {
            // ── Bitmap content ─────────────────────────────────────────────
            if (bitmap != null && !isRendering) {
                Image(
                    bitmap           = bitmap.asImageBitmap(),
                    contentDescription = "Wallpaper Preview",
                    modifier         = Modifier.fillMaxSize(),
                    contentScale     = ContentScale.Crop
                )
                
                // ── MOCKUP OVERLAYS ─────────────────────────────────────────
                Box(Modifier.fillMaxSize()) {
                    if (wallpaperType == "lockscreen") {
                        // Mock Lockscreen Clock & Date
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 45.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp).padding(bottom = 6.dp)
                            )
                            Text(
                                "09:41",
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(0.3f), blurRadius = 10f))
                            )
                            Text(
                                "Sunday, 5 May",
                                color = Color.White.copy(0.9f),
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(0.3f), blurRadius = 10f))
                            )
                        }
                        
                        // Mock Bottom Shortcuts
                        Row(
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 30.dp, start = 24.dp, end = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(0.3f)), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.FlashlightOn, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(0.3f)), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        // Mock Homescreen Status Bar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 20.dp, end = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("09:41", color = Color.White, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.SignalCellular4Bar, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Wifi, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.BatteryFull, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }

                        // Mock App Icons grid
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 50.dp, start = 20.dp, end = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            repeat(3) { rowIdx ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    repeat(4) { colIdx ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(0.20f))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(24.dp)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(Color.White.copy(0.35f))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Mock Homescreen Dock
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 20.dp)
                                .align(Alignment.BottomCenter)
                                .height(52.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(0.18f))
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(0.3f))
                                )
                            }
                        }
                    }
                    
                    // Home Indicator (Gesture Bar)
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).width(90.dp).height(3.dp).clip(CircleShape).background(Color.White))
                }
            }

            // ── Loading / shimmer overlay ──────────────────────────────────
            if (isRendering || bitmap == null) {
                ShimmerOverlay(
                    modifier = Modifier.fillMaxSize(),
                    showSpinner = isRendering
                )
            }
        }

        // ── Notch ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .width(72.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.Black)
        )
    }
}

// ── Shimmer loading overlay ────────────────────────────────────────────────────

@Composable
private fun ShimmerOverlay(modifier: Modifier, showSpinner: Boolean) {
    val shimmerOffset by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue  = 2f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF111116),
                    Color(0xFF1A1A22),
                    Color(0xFF111116)
                ),
                start = androidx.compose.ui.geometry.Offset(shimmerOffset * 1000f, 0f),
                end   = androidx.compose.ui.geometry.Offset((shimmerOffset + 1f) * 1000f, 0f)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (showSpinner) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    color       = OrangeAccent,
                    strokeWidth = 2.5.dp,
                    modifier    = Modifier.size(28.dp)
                )
                Text(
                    "Rendering…",
                    color    = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
