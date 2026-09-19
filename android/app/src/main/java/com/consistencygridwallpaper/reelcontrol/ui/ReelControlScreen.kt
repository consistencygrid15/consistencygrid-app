package com.consistencygridwallpaper.reelcontrol.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.consistencygridwallpaper.reelcontrol.data.PreferencesManager
import com.consistencygridwallpaper.reelcontrol.manager.ReelManager
import com.consistencygridwallpaper.repository.ReelControllerRepository
import com.consistencygridwallpaper.ui.compose.theme.*
import java.util.Calendar

// ─── OEM Detection ────────────────────────────────────────────────────────────
private fun isXiaomi() = Build.MANUFACTURER.lowercase().contains("xiaomi") ||
    Build.MANUFACTURER.lowercase().contains("redmi")
private fun isHuawei() = Build.MANUFACTURER.lowercase().contains("huawei") ||
    Build.MANUFACTURER.lowercase().contains("honor")
private fun isOppo() = Build.MANUFACTURER.lowercase().contains("oppo") ||
    Build.MANUFACTURER.lowercase().contains("oneplus")
private fun isVivo() = Build.MANUFACTURER.lowercase().contains("vivo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val reelRepo = remember { ReelControllerRepository(context) }

    var isEnabled by remember { mutableStateOf(prefs.isFeatureEnabled) }
    var refreshKey by remember { mutableStateOf(0) }
    var showRestrictedHelp by remember { mutableStateOf(false) }

    // ── Google Play compliance: prominent disclosure before Accessibility request ──
    // Show the disclosure screen if the user has NOT yet seen it. After they
    // tap "I Understand", mark it seen and open Accessibility Settings directly.
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
                isEnabled = prefs.isFeatureEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasAccessibility = remember(refreshKey) { isAccessibilityEnabled(context) }
    val hasOverlay       = remember(refreshKey) { Settings.canDrawOverlays(context) }
    val hasBatteryOpt    = remember(refreshKey) { isBatteryOptimizationIgnored(context) }

    // ── Full-screen disclosure overlay — shown before Accessibility Settings ──────
    if (showAccessibilityDisclosure) {
        OnboardingScreen(
            onComplete = {
                prefs.hasSeenAccessibilityDisclosure = true
                showAccessibilityDisclosure = false
                // Now open Accessibility Settings after user has acknowledged disclosure
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            },
            onDecline = {
                showAccessibilityDisclosure = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reel Control", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !hasAccessibility) {
                                showAccessibilityDisclosure = true
                            } else {
                                isEnabled = checked
                                reelRepo.setFeatureEnabled(checked)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = OrangeAccent),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgBase)
            )
        },
        containerColor = BgBase
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ─── Service Dead Banner (shown when OEM killed the service) ────────
            if (!hasAccessibility) {
                ServiceDeadBanner(
                    context = context,
                    onHelpClick = { showRestrictedHelp = true },
                    onFixClick = {
                        if (!prefs.hasSeenAccessibilityDisclosure) {
                            showAccessibilityDisclosure = true
                        } else {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {}
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))
            }

            // ─── Service Status Card ──────────────────────────────────────────
            ServiceStatusCard(
                hasAccessibility = hasAccessibility,
                hasOverlay = hasOverlay,
                hasBatteryOpt = hasBatteryOpt,
                context = context,
                onAccessibilityHelp = { showRestrictedHelp = true },
                onAccessibilityFix = {
                    if (!prefs.hasSeenAccessibilityDisclosure) {
                        showAccessibilityDisclosure = true
                    } else {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    }
                }
            )
            Spacer(Modifier.height(24.dp))

            // ─── OEM Autostart Card ───────────────────────────────────────────
            OemAutostartCard(
                hasAccessibility = hasAccessibility,
                hasOverlay = hasOverlay,
                hasBatteryOpt = hasBatteryOpt,
                prefs = prefs,
                context = context
            )
            Spacer(Modifier.height(24.dp))

            // ─── Hero Section: Today's Stats & Weekly Chart ─────────────────
            HeroStatsCard(prefs, refreshKey)
            Spacer(Modifier.height(24.dp))

            // ─── Scheduled Pause ─────────────────────────────────────────────
            ScheduledPauseCard(prefs, refreshKey)
            Spacer(Modifier.height(24.dp))

            // ─── Quick interactive focus controls ─────────────────────────────
            FocusSprintCard(prefs, refreshKey)
            Spacer(Modifier.height(24.dp))

            // ─── Blocking Options ───────────────────────────────────────────
            SectionHeader("FOCUS MODES")
            BlockingOptionsCard(prefs, reelRepo, refreshKey)
            Spacer(Modifier.height(24.dp))

            // ─── App Controls ───────────────────────────────────────────────
            SectionHeader("APP LIMITS")
            val apps = listOf(
                AppMeta("Instagram", "com.instagram.android"),
                AppMeta("YouTube", "com.google.android.youtube")
                // AppMeta("Snapchat", "com.snapchat.android"),  // DISABLED — removed from tracking
                // AppMeta("TikTok",   "com.zhiliaoapp.musically") // DISABLED — removed from tracking
            )
            apps.forEach { app ->
                AppControlCard(app, prefs, reelRepo, refreshKey)
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(24.dp))

            // ─── Reset Button ───────────────────────────────────────────────
            Button(
                onClick = {
                    prefs.resetDailyProgress()
                    ReelManager.getInstance(context).clearSnooze()
                    refreshKey++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset Today's Progress", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))
            SafetyNote()
            Spacer(Modifier.height(60.dp))
        }
    }

    if (showRestrictedHelp) {
        RestrictedHelpDialog(
            context = context,
            onDismiss = { showRestrictedHelp = false }
        )
    }
}

@Composable
private fun FocusSprintCard(prefs: PreferencesManager, refreshKey: Int) {
    var activeUntil by remember(refreshKey) { mutableStateOf(prefs.scheduledPauseUntilMs) }
    val isActive = activeUntil > System.currentTimeMillis()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF172554),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF38BDF8).copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF7DD3FC))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Focus Sprint", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (isActive) "Tracking paused for a focused break"
                        else "Pause tracking instantly and protect your next block of time",
                        color = Color(0xFFBFDBFE),
                        fontSize = 12.sp
                    )
                }
                if (isActive) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Sprint active", tint = Color(0xFF4ADE80))
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    val selected = isActive && (activeUntil - System.currentTimeMillis()) in
                        ((minutes - 1) * 60_000L)..((minutes + 1) * 60_000L)
                    Surface(
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFF38BDF8).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (selected) Color(0xFF7DD3FC) else Color.Transparent),
                        onClick = {
                            val target = System.currentTimeMillis() + minutes * 60_000L
                            prefs.scheduledPauseUntilMs = target
                            activeUntil = target
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${minutes}m", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (isActive) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        prefs.clearPause()
                        activeUntil = 0L
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Resume tracking", color = Color(0xFF7DD3FC), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Hero Stats & Chart ────────────────────────────────────────────────────────
@Composable
private fun HeroStatsCard(prefs: PreferencesManager, refreshKey: Int) {
    val context = LocalContext.current

    // Observe live StateFlows from ReelManager so the dashboard updates in real-time
    // while the user is on this screen (without needing to navigate away and back).
    val reelManager = remember { ReelManager.getInstance(context) }
    val liveReelCount by reelManager.reelCount.collectAsStateWithLifecycle()
    val liveWatchTimeSec by reelManager.watchTime.collectAsStateWithLifecycle()

    // Snapshot totals across all apps from prefs (refreshes on each resume/reset).
    // The active-package live values come from StateFlow above and override the snapshot
    // for the active package when we're currently in reel mode.
    val snapTotalReels = remember(refreshKey) {
        PreferencesManager.TRACKED_PACKAGES.sumOf { prefs.getTodayReelCount(it) }
    }
    val snapTotalMins  = remember(refreshKey) {
        PreferencesManager.TRACKED_PACKAGES.sumOf { prefs.getTodayWatchTimeSeconds(it) } / 60
    }

    // If the manager is tracking an active package, add its live delta on top of the snapshot.
    // This keeps the number moving in real-time without double-counting resting packages.
    val activePkg = remember(refreshKey) { reelManager.getActivePackage() }
    val snapActiveReels = remember(refreshKey) {
        if (activePkg.isNotEmpty()) prefs.getTodayReelCount(activePkg) else 0
    }
    val snapActiveTimeMins = remember(refreshKey) {
        if (activePkg.isNotEmpty()) prefs.getTodayWatchTimeSeconds(activePkg) / 60L else 0L
    }
    val totalReels = if (activePkg.isNotEmpty()) {
        snapTotalReels - snapActiveReels + liveReelCount
    } else snapTotalReels
    val totalMins = if (activePkg.isNotEmpty()) {
        snapTotalMins - snapActiveTimeMins + (liveWatchTimeSec / 60L)
    } else snapTotalMins

    val streak = remember(refreshKey) { prefs.noReelStreak }
    val weeklyCounts = remember(refreshKey) { prefs.getWeeklyReelCountsAll() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = BgCard,
        border = BorderStroke(1.dp, Color(0xFF27272A))
    ) {
        Column(Modifier.padding(20.dp)) {
            // Top Row: Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Today's Reels", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("$totalReels", color = OrangeAccent, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${totalMins}m watch time", color = TextMuted, fontSize = 13.sp)
                }
                
                // Streak Badge
                Surface(
                    color = if (streak > 0) OrangeAccent.copy(alpha = 0.12f) else Color.Gray.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (streak > 0) OrangeAccent.copy(alpha = 0.3f) else Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (streak > 0) Icons.Default.Whatshot else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (streak > 0) OrangeAccent else Color(0xFF22C55E),
                            modifier = Modifier.size(16.dp)
                        )
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                "$streak days",
                                color = if (streak > 0) OrangeAccent else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("clean", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            // Weekly Chart
            Text("Last 7 Days", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            WeeklyBarChart(counts = weeklyCounts)
        }
    }
}

@Composable
private fun WeeklyBarChart(counts: List<Int>) {
    val maxCount = maxOf(counts.maxOrNull() ?: 1, 10).toFloat()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        counts.forEachIndexed { index, count ->
            val heightPct = (count.toFloat() / maxCount).coerceIn(0.1f, 1f)
            val isToday = index == counts.size - 1
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                // Value tooltip
                if (count > 0) {
                    Text(
                        "$count",
                        color = if (isToday) OrangeAccent else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                
                // Animated bar
                val animatedHeight by animateFloatAsState(targetValue = heightPct)
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight(animatedHeight)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (isToday) OrangeAccent else Color.Gray.copy(alpha = 0.2f))
                )
            }
        }
    }
}

// ─── Scheduled Pause ───────────────────────────────────────────────────────────
@Composable
private fun ScheduledPauseCard(prefs: PreferencesManager, refreshKey: Int) {
    val context = LocalContext.current
    var isPaused by remember(refreshKey) { mutableStateOf(prefs.isPaused()) }
    var pauseUntilMs by remember(refreshKey) { mutableStateOf(prefs.scheduledPauseUntilMs) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = BorderStroke(1.dp, if (isPaused) OrangeAccent.copy(alpha = 0.5f) else Color(0xFF27272A))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isPaused) OrangeAccent.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPaused) Icons.Default.Pause else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (isPaused) OrangeAccent else TextPrimary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isPaused) "Tracking Paused" else "Scheduled Pause",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (isPaused) {
                    val cal = Calendar.getInstance().apply { timeInMillis = pauseUntilMs }
                    val timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                    Text("Resumes at $timeStr", color = OrangeAccent, fontSize = 13.sp)
                } else {
                    Text("Take a break from tracking", color = TextMuted, fontSize = 13.sp)
                }
            }
            if (isPaused) {
                TextButton(onClick = {
                    prefs.clearPause()
                    isPaused = false
                }) {
                    Text("RESUME", color = OrangeAccent, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = {
                    val cal = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour, min ->
                            val target = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, min)
                                set(Calendar.SECOND, 0)
                                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
                            }
                            prefs.scheduledPauseUntilMs = target.timeInMillis
                            pauseUntilMs = target.timeInMillis
                            isPaused = true
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                }) {
                    Text("SET", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Blocking Options ──────────────────────────────────────────────────────────
@Composable
private fun BlockingOptionsCard(prefs: PreferencesManager, reelRepo: ReelControllerRepository, refreshKey: Int) {
    var blockModeEnabled by remember(refreshKey) { mutableStateOf(prefs.isBlockModeEnabled) }
    var hardBlockEnabled by remember(refreshKey) { mutableStateOf(prefs.isHardBlockEnabled) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = BgCard
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mindful Pause Overlay", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Intercept reel entry and require a 5s hold to continue", color = TextMuted, fontSize = 13.sp)
                }
                Switch(
                    checked = blockModeEnabled,
                    onCheckedChange = { blockModeEnabled = it; reelRepo.setBlockModeEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = OrangeAccent)
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF27272A))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hard Block (Instant Exit)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Force exit the app immediately instead of showing overlay", color = TextMuted, fontSize = 13.sp)
                }
                Switch(
                    checked = hardBlockEnabled,
                    onCheckedChange = { hardBlockEnabled = it; reelRepo.setHardBlockEnabled(it) },
                    enabled = blockModeEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = RedError)
                )
            }
        }
    }
}

// ─── App Controls ─────────────────────────────────────────────────────────────
data class AppMeta(val name: String, val pkg: String)

private fun getAppIconAndColor(pkg: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> {
    return when (pkg) {
        "com.instagram.android" -> Icons.Default.CameraAlt to Color(0xFFE1306C) // Instagram Pink
        "com.google.android.youtube" -> Icons.Default.PlayArrow to Color(0xFFFF0000) // YouTube Red
        "com.snapchat.android" -> Icons.Default.ChatBubbleOutline to Color(0xFFFFCC00) // Snapchat Yellow
        "com.zhiliaoapp.musically" -> Icons.Default.MusicNote to Color(0xFF00F2FE) // TikTok Teal
        else -> Icons.Default.Android to Color.Gray
    }
}

@Composable
private fun AppControlCard(app: AppMeta, prefs: PreferencesManager, reelRepo: ReelControllerRepository, refreshKey: Int) {
    var expanded by remember { mutableStateOf(false) }
    var enabled by remember(refreshKey) { mutableStateOf(prefs.isAppEnabled(app.pkg)) }

    val todayReels = remember(refreshKey) { prefs.getTodayReelCount(app.pkg) }
    val reelLimit = remember(refreshKey) { prefs.getReelLimit(app.pkg) }
    
    val progressPct = if (reelLimit > 0) (todayReels.toFloat() / reelLimit).coerceIn(0f, 1f) else 0f
    val (appIcon, brandColor) = remember { getAppIconAndColor(app.pkg) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = BorderStroke(1.dp, if (expanded) brandColor.copy(alpha = 0.4f) else Color.Transparent),
        onClick = { expanded = !expanded }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sleek brand-specific icon container (no emojis!)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(brandColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, brandColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = appIcon,
                        contentDescription = app.name,
                        tint = brandColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    
                    if (reelLimit > 0 && enabled) {
                        // Visual progress bar
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.Gray.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressPct)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (progressPct >= 1f) RedError else OrangeAccent)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "$todayReels / $reelLimit",
                                color = if (progressPct >= 1f) RedError else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = if (!enabled) "Tracking disabled" else "No limit set",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { 
                        enabled = it
                        reelRepo.setAppEnabled(app.pkg, it)
                        if (it && reelLimit == 0) {
                            // Automatically set default moderate limits on first enable
                            reelRepo.setReelLimit(app.pkg, 30)
                            reelRepo.setTimeLimit(app.pkg, 20)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = OrangeAccent
                    )
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 20.dp)) {
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))
                    
                    Text("LIMIT PRESETS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    
                    var currentReelLimit by remember(refreshKey) { mutableStateOf(reelLimit) }
                    var currentTimeLimit by remember(refreshKey) { mutableStateOf(prefs.getTimeLimit(app.pkg)) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PresetButton("Strict", "15", "10m", currentReelLimit == 15, Modifier.weight(1f), brandColor) {
                            reelRepo.setReelLimit(app.pkg, 15)
                            reelRepo.setTimeLimit(app.pkg, 10)
                            currentReelLimit = 15
                            currentTimeLimit = 10
                            enabled = true
                            reelRepo.setAppEnabled(app.pkg, true)
                        }
                        PresetButton("Mod", "30", "20m", currentReelLimit == 30, Modifier.weight(1f), brandColor) {
                            reelRepo.setReelLimit(app.pkg, 30)
                            reelRepo.setTimeLimit(app.pkg, 20)
                            currentReelLimit = 30
                            currentTimeLimit = 20
                            enabled = true
                            reelRepo.setAppEnabled(app.pkg, true)
                        }
                        PresetButton("Light", "50", "30m", currentReelLimit == 50, Modifier.weight(1f), brandColor) {
                            reelRepo.setReelLimit(app.pkg, 50)
                            reelRepo.setTimeLimit(app.pkg, 30)
                            currentReelLimit = 50
                            currentTimeLimit = 30
                            enabled = true
                            reelRepo.setAppEnabled(app.pkg, true)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("CUSTOM LIMITS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))

                    // Premium Increment/Decrement Editors for updating limits
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Reels Count Limit", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Max reels per day", color = TextMuted, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (currentReelLimit > 5) {
                                        currentReelLimit -= 5
                                        reelRepo.setReelLimit(app.pkg, currentReelLimit)
                                        enabled = true
                                        reelRepo.setAppEnabled(app.pkg, true)
                                    }
                                },
                                modifier = Modifier.size(36.dp).background(Color.Gray.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "$currentReelLimit",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            IconButton(
                                onClick = {
                                    currentReelLimit += 5
                                    reelRepo.setReelLimit(app.pkg, currentReelLimit)
                                    val todayTimeMins = (prefs.getTodayWatchTimeSeconds(app.pkg) / 60L).toInt()
                                    if (currentTimeLimit > 0 && todayTimeMins >= currentTimeLimit) {
                                        currentTimeLimit = maxOf(currentTimeLimit + 5, todayTimeMins + 5)
                                        reelRepo.setTimeLimit(app.pkg, currentTimeLimit)
                                    }
                                    enabled = true
                                    reelRepo.setAppEnabled(app.pkg, true)
                                },
                                modifier = Modifier.size(36.dp).background(Color.Gray.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Daily Time Limit", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Max minutes per day", color = TextMuted, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (currentTimeLimit > 5) {
                                        currentTimeLimit -= 5
                                        reelRepo.setTimeLimit(app.pkg, currentTimeLimit)
                                        enabled = true
                                        reelRepo.setAppEnabled(app.pkg, true)
                                    }
                                },
                                modifier = Modifier.size(36.dp).background(Color.Gray.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "${currentTimeLimit}m",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            IconButton(
                                onClick = {
                                    currentTimeLimit += 5
                                    reelRepo.setTimeLimit(app.pkg, currentTimeLimit)
                                    val todayReels = prefs.getTodayReelCount(app.pkg)
                                    if (currentReelLimit > 0 && todayReels >= currentReelLimit) {
                                        currentReelLimit = maxOf(currentReelLimit + 5, todayReels + 5)
                                        reelRepo.setReelLimit(app.pkg, currentReelLimit)
                                    }
                                    enabled = true
                                    reelRepo.setAppEnabled(app.pkg, true)
                                },
                                modifier = Modifier.size(36.dp).background(Color.Gray.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Delete / Disable Limit Button
                    OutlinedButton(
                        onClick = {
                            enabled = false
                            reelRepo.setAppEnabled(app.pkg, false)
                            reelRepo.setReelLimit(app.pkg, 0)
                            reelRepo.setTimeLimit(app.pkg, 0)
                            expanded = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError),
                        border = BorderStroke(1.dp, RedError.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = RedError, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete / Disable Limit", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    title: String,
    reels: String,
    mins: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    brandColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) brandColor.copy(alpha = 0.12f) else Color.Gray.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (isSelected) brandColor.copy(alpha = 0.6f) else Color.Transparent),
        onClick = onClick
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = if (isSelected) brandColor else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("$reels r · $mins", color = if (isSelected) brandColor.copy(alpha = 0.8f) else TextMuted, fontSize = 11.sp)
        }
    }
}

// ─── Utilities ────────────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SafetyNote() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        color = BlueInfo.copy(alpha = 0.1f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Security, contentDescription = null, tint = BlueInfo, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Privacy First — This feature only tracks scroll events for digital wellbeing. " +
                "No personal data, content, or usage is ever collected or shared.",
                color = BlueInfo,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
    }
}

// ─── Service Status Card ───────────────────────────────────────────────────────
@Composable
private fun ServiceStatusCard(
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    hasBatteryOpt: Boolean,
    context: Context,
    onAccessibilityHelp: () -> Unit,
    onAccessibilityFix: () -> Unit
) {
    val allOk = hasAccessibility && hasOverlay

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .then(
                if (!allOk) Modifier.border(1.dp, RedError.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                else Modifier.border(1.dp, Color(0xFF22C55E).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (!allOk) RedError.copy(alpha = 0.1f) else Color(0xFF22C55E).copy(alpha = 0.1f)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (allOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (allOk) Color(0xFF22C55E) else RedError,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (allOk) "Service Active" else "Action Required",
                    color = if (allOk) Color(0xFF22C55E) else RedError,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            PermissionRow(
                label = "Accessibility Service",
                hint = if (!hasAccessibility) "Tap FIX → find 'ConsistencyGrid' → ENABLE" else "Active",
                isOk = hasAccessibility,
                buttonLabel = "FIX NOW",
                // Delegates to parent which shows disclosure screen first if needed
                onFix = onAccessibilityFix,
                onHelp = onAccessibilityHelp
            )

            Spacer(Modifier.height(10.dp))

            PermissionRow(
                label = "Display Over Apps",
                hint = if (!hasOverlay) "Required for HUD overlay" else "Granted",
                isOk = hasOverlay,
                buttonLabel = "GRANT",
                onFix = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (e: Exception) {}
                }
            )

            Spacer(Modifier.height(10.dp))

            PermissionRow(
                label = "Battery Optimization",
                hint = if (!hasBatteryOpt) "OEM may kill service on restart" else "Exempted",
                isOk = hasBatteryOpt,
                buttonLabel = "EXEMPT",
                onFix = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (e: Exception) { }
                }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    hint: String,
    isOk: Boolean,
    buttonLabel: String,
    onFix: () -> Unit,
    onHelp: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isOk) Color(0xFF4ADE80) else Color(0xFFFC8181),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = TextMuted, fontSize = 11.sp)
            if (!isOk && onHelp != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Blocked? Got \"Restricted Setting\"? Tap here",
                    color = OrangeAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onHelp() }
                )
            }
        }
        if (!isOk) {
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onFix,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonLabel, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    return try {
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        services.contains(context.packageName, ignoreCase = true)
    } catch (e: Exception) { false }
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    } catch (e: Exception) { true }
}

// ─── Service Dead Banner ─────────────────────────────────────────────
@Composable
private fun ServiceDeadBanner(context: Context, onHelpClick: () -> Unit, onFixClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = RedError.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, RedError)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = RedError,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Reel Counter Stopped",
                color = RedError,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your phone turned off the reel tracker. Open Accessibility Settings and re-enable 'ConsistencyGrid'. It only takes 5 seconds!",
                color = TextPrimary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onFixClick,
                colors = ButtonDefaults.buttonColors(containerColor = RedError),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("FIX NOW — Open Accessibility", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onHelpClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Got \"Restricted Setting\" popup? Fix here", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

// ─── OEM Autostart Card ────────────────────────────────────────────────────────
@Composable
private fun OemAutostartCard(
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    hasBatteryOpt: Boolean,
    prefs: PreferencesManager,
    context: Context
) {
    var isDismissed by remember { mutableStateOf(prefs.isAutostartDismissed) }
    if (isDismissed) return

    val (oemName, oemIntent) = remember { getOemAutostartIntent(context) }
    if (oemIntent == null) return // Not an OEM that needs this — hide card

    // Only show Autostart if some core permission is lacking
    val isAnyPermissionLacking = !hasAccessibility || !hasOverlay || !hasBatteryOpt
    if (!isAnyPermissionLacking) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = OrangeAccent.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.4f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .padding(end = 20.dp), // Space for the close button
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(OrangeAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = OrangeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Enable Autostart ($oemName)",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "Required so the reel counter works after you close the app",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        try { context.startActivity(oemIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
                        catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ENABLE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            IconButton(
                onClick = {
                    prefs.isAutostartDismissed = true
                    isDismissed = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** Returns pair of (OEM display name, Intent to open the autostart settings).
 *  Returns null intent if not a known OEM ROM that needs this. */
private fun getOemAutostartIntent(context: Context): Pair<String, Intent?> {
    return when {
        isXiaomi() -> "Xiaomi" to listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            Intent().apply {
                action = "miui.intent.action.APP_PERM_EDITOR"
                setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            }
        ).firstOrNull { isIntentResolvable(context, it) }

        isHuawei() -> "Huawei" to listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }
        ).firstOrNull { isIntentResolvable(context, it) }

        isOppo() -> "OnePlus/Oppo" to listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                )
            }
        ).firstOrNull { isIntentResolvable(context, it) }

        isVivo() -> "Vivo" to listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
        ).firstOrNull { isIntentResolvable(context, it) }

        else -> "" to null
    }
}

private fun isIntentResolvable(context: Context, intent: Intent): Boolean {
    return try {
        context.packageManager.resolveActivity(intent, 0) != null
    } catch (_: Exception) { false }
}

@Composable
private fun RestrictedHelpDialog(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = OrangeAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Bypass Restricted Setting",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Android 13+ restricts sideloaded apps from enabling Accessibility by default.",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "To unlock and allow this setting, please follow these steps:",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                
                val steps = listOf(
                    "Tap 'OPEN APP INFO' below to open settings.",
                    "Tap the three dots (⋮) in the top-right corner.",
                    "Select 'Allow restricted settings'.",
                    "Verify your PIN/fingerprint, then return here to enable Reel Control."
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    steps.forEachIndexed { idx, step ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(OrangeAccent.copy(alpha = 0.12f), CircleShape)
                                    .border(1.dp, OrangeAccent.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = OrangeAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = step,
                                color = TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {}
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("OPEN APP INFO", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextMuted)
            }
        },
        containerColor = BgCard,
        shape = RoundedCornerShape(16.dp)
    )
}
