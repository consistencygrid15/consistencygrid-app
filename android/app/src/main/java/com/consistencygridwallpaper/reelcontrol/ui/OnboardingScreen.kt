package com.consistencygridwallpaper.reelcontrol.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AccessibilityDisclosureScreen — Google Play-compliant in-app disclosure.
 *
 * Shown BEFORE requesting Accessibility permission. Explains exactly what data
 * is accessed, why, and what is explicitly NOT done.
 *
 * Provides explicit Agree & Decline options to satisfy Google Play User Data
 * and AccessibilityService API prominent disclosure requirements.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onDecline: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Animated entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 8 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0F0F), Color(0xFF1A1A1A))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))

                // ── Icon ──────────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF7A00).copy(alpha = 0.15f))
                        .border(2.dp, Color(0xFFFF7A00).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Accessibility,
                        contentDescription = null,
                        tint = Color(0xFFFF7A00),
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "AccessibilityService API Disclosure",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Reel Control requires Android AccessibilityService API to deliver short-video wellbeing controls",
                    fontSize = 13.sp,
                    color = Color(0xFF9CA3AF),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(24.dp))

                // ── What we DO ────────────────────────────────────────────────
                DisclosureCard(
                    title = "How AccessibilityService API is used",
                    accentColor = Color(0xFFFF7A00),
                    icon = Icons.Default.Check,
                    items = listOf(
                        "Detect when Instagram Reels or YouTube Shorts are in the foreground",
                        "Count vertical swipe and scroll events to measure daily short-form video watch time",
                        "Display a floating HUD overlay to show remaining video time limits set by you",
                        "Trigger a mindful pause overlay when your personal daily reel limit is reached"
                    )
                )

                Spacer(Modifier.height(16.dp))

                // ── Privacy & Safety Guarantee ────────────────────────────────
                DisclosureCard(
                    title = "Strict Data Privacy Safeguards",
                    accentColor = Color(0xFF22C55E),
                    icon = Icons.Default.Shield,
                    items = listOf(
                        "All scroll tracking occurs strictly on-device; no data is ever transmitted or uploaded",
                        "NEVER reads typed text, passwords, messages, contacts, or financial details",
                        "NEVER records audio, photos, videos, or screen contents",
                        "NEVER modifies system settings without explicit user authorization",
                        "Only active while watching supported short-video apps"
                    )
                )

                Spacer(Modifier.height(16.dp))

                // ── Tracked apps info ─────────────────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1F1F1F)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Monitored apps (scroll & window state only)",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("📸 Instagram", "▶️ YouTube").forEach { app ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2A2A2A)
                                ) {
                                    Text(
                                        app,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 11.sp,
                                        color = Color(0xFFD1D5DB)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Explicit Consent Buttons (Play Policy Requirement) ───────
                // 1. Agree & Continue (Primary)
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7A00))
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Agree & Continue to Settings",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 2. Decline / Not Now (Secondary)
                OutlinedButton(
                    onClick = { onDecline?.invoke() ?: onComplete() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF4B5563))
                ) {
                    Text(
                        "Decline / Not Now",
                        color = Color(0xFF9CA3AF),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Privacy Policy link — required before permission request
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://consistencygrid.com/privacy-policy"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Security,
                        null,
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Read Full Privacy Policy",
                        color = Color(0xFF6B7280),
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DisclosureCard(
    title: String,
    accentColor: Color,
    icon: ImageVector,
    items: List<String>
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1A1A)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            items.forEach { item ->
                Row(
                    modifier = Modifier.padding(bottom = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.7f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        item,
                        color = Color(0xFFD1D5DB),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}
