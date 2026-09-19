package com.consistencygridwallpaper.ui.compose.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.ui.compose.theme.*

enum class SupportDocType(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    HELP_SUPPORT(
        title = "Help & Support",
        subtitle = "FAQs and technical assistance",
        icon = Icons.Outlined.HelpOutline,
        accentColor = OrangeAccent
    ),
    PRIVACY_POLICY(
        title = "Privacy Policy",
        subtitle = "Data security & accessibility disclosures",
        icon = Icons.Outlined.Security,
        accentColor = VioletAccent
    ),
    TERMS_OF_SERVICE(
        title = "Terms of Service",
        subtitle = "Rules, policies, and subscriptions",
        icon = Icons.Outlined.Description,
        accentColor = Color(0xFF00BFA5)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportViewerSheet(
    docType: SupportDocType,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = docType.accentColor.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(BgCard)
        ) {
            // ── Header Bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(docType.accentColor.copy(alpha = 0.12f))
                        .border(1.dp, docType.accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = docType.icon,
                        contentDescription = null,
                        tint = docType.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = docType.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = docType.subtitle,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                // Close Button
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            HorizontalDivider(color = GlassStroke, thickness = 0.5.dp)

            // ── Native Document Content ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(BgBase)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    when (docType) {
                        SupportDocType.HELP_SUPPORT -> {
                            item { NativeHelpSupportHeader(context = context) }
                            item { NativeFaqSection() }
                        }
                        SupportDocType.PRIVACY_POLICY -> {
                            item { NativePrivacyPolicyDocument() }
                        }
                        SupportDocType.TERMS_OF_SERVICE -> {
                            item { NativeTermsOfServiceDocument() }
                        }
                    }
                }
            }
        }
    }
}

// ── Native Help & Support ─────────────────────────────────────────────────────

@Composable
private fun NativeHelpSupportHeader(context: Context) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Need Personal Help?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Contact our support team directly via email.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@consistencygrid.com")
                        putExtra(Intent.EXTRA_SUBJECT, "ConsistencyGrid Support Request")
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, "Send Email"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Email client: support@consistencygrid.com", android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Email Us", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun NativeFaqSection() {
    val faqs = remember {
        listOf(
            "Why does Reel Control require Accessibility Permission?" to
                    "Accessibility permission is used strictly to count vertical scroll sweeps inside targeted short-form video apps (Instagram Reels and YouTube Shorts) to enforce your daily time limits.\n\n" +
                    "Zero personal data, key presses, text inputs, or screen contents are ever recorded, stored, or transmitted to any server.",

            "My wallpaper stopped updating automatically at midnight. How to fix?" to
                    "Some Android devices (Xiaomi MIUI, Samsung OneUI, OnePlus, Vivo) aggressively suspend background services to save battery.\n\n" +
                    "To ensure 100% reliable updates:\n" +
                    "1. Go to System Settings → Apps → ConsistencyGrid.\n" +
                    "2. Enable 'Autostart' (for Xiaomi/Oppo/Vivo).\n" +
                    "3. Set Battery Usage to 'Unrestricted'.\n" +
                    "4. Turn off 'Pause app activity if unused'.",

            "How does offline habit tracking & cloud sync work?" to
                    "ConsistencyGrid is designed offline-first. Every habit tick, streak calculation, and goal progress update is saved locally on your device in real-time.\n\n" +
                    "When your device connects to the internet, local changes seamlessly sync with our secure cloud servers in the background.",

            "How do I customize wallpaper themes and target screens?" to
                    "Open the Wallpaper Generator tab from the home dashboard or settings page. You can customize colors, grid density, header metrics, and select target screen options ('Home Screen', 'Lock Screen', or 'Both').",

            "How do I permanently delete my account and data?" to
                    "Go to Settings → Account → Delete Account. Confirm deletion through the 3-step confirmation sheet. All local database tables and cloud records linked to your account will be immediately and permanently erased."
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Frequently Asked Questions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        faqs.forEach { (question, answer) ->
            var expanded by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke),
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = question,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = OrangeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (expanded) {
                        HorizontalDivider(color = GlassStroke, thickness = 0.5.dp)
                        Text(answer, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

// ── Native Privacy Policy ─────────────────────────────────────────────────────

@Composable
private fun NativePrivacyPolicyDocument() {
    val sections = remember {
        listOf(
            "1. Introduction & Core Philosophy" to
                    "ConsistencyGrid respects your privacy above all else. Our core architecture is built offline-first with zero telemetry tracking. We do not track your location, sell your personal data, or run third-party advertising trackers.",

            "2. Information We Collect" to
                    "• Account Information: Email address and authentication token provided via Google Sign-In or Email Registration.\n" +
                    "• User Content: Habits, goals, reminders, and streak counters created inside the app.\n" +
                    "• Technical Data: Device timezone and local date used exclusively to calculate daily grid rollovers.",

            "3. Accessibility API Disclosure (Reel Control)" to
                    "ConsistencyGrid includes an optional Digital Wellbeing feature called 'Reel Control'. This feature uses Android's AccessibilityService API (BIND_ACCESSIBILITY_SERVICE).\n\n" +
                    "• Purpose: Detect scroll gestures in user-selected short-form video applications (Instagram Reels and YouTube Shorts) to enforce user-configured time limits.\n" +
                    "• Data Guarantee: NO text, typed input, screen recordings, media content, or personal identifiers are ever logged, captured, or transmitted. The Accessibility API operates purely locally on your device.",

            "4. Local & Encrypted Storage" to
                    "Your session tokens and authentication credentials are encrypted locally using AES256 encryption via Android's EncryptedSharedPreferences. Habit records are persisted inside an encrypted Room SQLite database.",

            "5. Data Sharing & Third Parties" to
                    "We do NOT sell, rent, or trade your personal data. Authentication is handled securely through Google Firebase Auth and standard HTTPS OAuth endpoints. Information is shared strictly when required by law.",

            "6. Data Retention & Deletion Rights" to
                    "You retain full ownership of your data. You can log out at any time via Settings → Log Out. Logging out purges local session tokens and resets account state on your device."
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VioletAccent.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Privacy Highlights", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Last updated: July 2026 • Effective for all ConsistencyGrid users",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        sections.forEach { (title, content) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VioletAccent)
                    HorizontalDivider(color = GlassStroke, thickness = 0.5.dp)
                    Text(content, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
                }
            }
        }
    }
}

// ── Native Terms of Service ───────────────────────────────────────────────────

@Composable
private fun NativeTermsOfServiceDocument() {
    val sections = remember {
        listOf(
            "1. Acceptance of Terms" to
                    "By creating an account or using ConsistencyGrid, you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not use the application.",

            "2. Description of Service" to
                    "ConsistencyGrid provides dynamic habit grid visualization, dynamic Live Wallpaper generation, goal tracking, AppWidgets, and Digital Wellbeing scroll limiting features for Android mobile devices.",

            "3. User Accounts & Security" to
                    "You are responsible for maintaining the confidentiality of your login credentials. You agree to notify us immediately of any unauthorized access to your account.",

            "4. Subscriptions & Payment Terms" to
                    "Premium features and subscription plans are processed through official in-app purchases or authorized payment gateways. Subscriptions auto-renew unless cancelled prior to the end of the billing period.",

            "5. Acceptable Use Policy" to
                    "You agree not to modify, reverse engineer, decompile, or attempt to extract source code from the application kernel, or use the service for illegal or unauthorized activities.",

            "6. Disclaimer of Warranties & Limitation of Liability" to
                    "ConsistencyGrid is provided 'as is' without warranties of any kind. In no event shall ConsistencyGrid be liable for any indirect, incidental, or consequential damages resulting from app usage or device performance settings."
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00BFA5).copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Terms of Service Agreement", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Effective Date: July 2026 • Official Application Terms",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        sections.forEach { (title, content) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00BFA5))
                    HorizontalDivider(color = GlassStroke, thickness = 0.5.dp)
                    Text(content, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
                }
            }
        }
    }
}
