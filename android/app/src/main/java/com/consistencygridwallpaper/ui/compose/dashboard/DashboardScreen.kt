package com.consistencygridwallpaper.ui.compose.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.reelcontrol.data.PreferencesManager
import com.consistencygridwallpaper.storage.room.GoalEntity
import com.consistencygridwallpaper.storage.room.HabitEntity
import com.consistencygridwallpaper.ui.compose.theme.*
import java.util.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import com.consistencygridwallpaper.ui.compose.tour.tourTarget
import com.consistencygridwallpaper.ui.compose.tour.TourManager
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File

@Composable
fun DashboardScreen(
    vm: DashboardViewModel = viewModel(),
    onOpenWeb: (url: String, title: String) -> Unit = { _, _ -> },
    onOpenWallpaper: () -> Unit = {},
    onOpenReelControl: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    onOpenStreaks: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    onOpenWidgets: () -> Unit = {},
    onOpenSubscription: () -> Unit = {}
) {
    val habits       by vm.habits.collectAsStateWithLifecycle()
    val todayLogs    by vm.todayLogs.collectAsStateWithLifecycle()
    val stats        by vm.stats.collectAsStateWithLifecycle()
    val goals        by vm.goals.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val profile      by vm.profile.collectAsStateWithLifecycle()

    val context   = LocalContext.current
    val prefs     = remember { UserPrefs(context) }
    val reelPrefs = remember { PreferencesManager(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val profilePhotoPath = remember(refreshKey) { prefs.getProfilePhotoPath() }
    val displayName      = profile.name.trim()
    val avatarInitials   = remember(displayName) {
        val parts = displayName.split(" ").filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
            parts.size == 1 -> parts.first().take(2).uppercase()
            else            -> ""
        }
    }

    var wallpaperTarget by remember(refreshKey) { mutableStateOf(prefs.getWallpaperTarget()) }
    var updateMode by remember(refreshKey) { mutableStateOf(prefs.getWallpaperUpdateMode()) }
    var showTargetDialog by remember { mutableStateOf(false) }
    val lastUpdateDate  = remember(refreshKey) { prefs.getLastUpdateDate() }
    val isReelEnabled   = remember(refreshKey) { reelPrefs.isFeatureEnabled }
    val isAccessEnabled = remember(refreshKey) { isAccessibilityEnabled(context) }
    val todayReelsCount = remember(refreshKey) {
        PreferencesManager.TRACKED_PACKAGES.sumOf { pkg ->
            reelPrefs.getTodayReelCount(pkg)
        }
    }

    var showTour by remember { mutableStateOf(false) }
    var currentTourStep by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val targetRects = remember { mutableStateMapOf<Int, Rect>() }

    LaunchedEffect(refreshKey) {
        showTour = false  // old per-screen tour disabled — global tour now used
    }

    LaunchedEffect(currentTourStep, showTour) {
        if (showTour) {
            val scrollIndex = when (currentTourStep) {
                0 -> 4 // Wallpaper Card (Control Hub)
                1 -> 1 // Progress Banner
                2 -> 2 // Stats Row
                3 -> 4 // Widgets Card (Control Hub)
                4 -> 4 // Reel Card (Control Hub)
                5 -> 7 // Active Goals
                6 -> 6 // Quick Actions
                else -> 0
            }
            listState.scrollToItem(scrollIndex)
        }
    }

    // ── Global tour scroll: ensure target item is visible ─────────────────────
    val globalTourStep = TourManager.currentStepIndex
    LaunchedEffect(globalTourStep) {
        if (TourManager.isActive && TourManager.currentStep?.screenRoute == "dashboard") {
            val scrollIndex = when (TourManager.currentStep?.targetKey) {
                "dashboard_progress_ring"  -> 1
                "dashboard_reel_card"      -> 4
                "dashboard_wallpaper_card" -> 4
                "dashboard_quick_actions"  -> 6
                else                       -> 0
            }
            listState.animateScrollToItem(scrollIndex)
        }
    }

    val cal = Calendar.getInstance()
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    
    val (greeting, greetingIcon, greetingIconColor) = remember(hour) {
        when {
            hour < 12 -> Triple("Good Morning", Icons.Default.LightMode, Color(0xFFFBBF24))
            hour < 17 -> Triple("Good Afternoon", Icons.Default.WbSunny, Color(0xFFF59E0B))
            else      -> Triple("Good Evening", Icons.Default.DarkMode, Color(0xFF818CF8))
        }
    }
    
    val wishText = remember(greeting, displayName) {
        if (displayName.isNotBlank()) "$greeting, $displayName" else greeting
    }

    val dayName = remember {
        val days = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        days[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }

    val doneToday    = stats.doneToday
    val totalHabits  = stats.totalHabits
    val rawProgress  = if (totalHabits > 0) doneToday.toFloat() / totalHabits else 0f
    val animProgress by animateFloatAsState(
        targetValue   = rawProgress,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label         = "arcProgress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state               = listState,
            modifier            = Modifier.fillMaxSize().background(BgBase),
            contentPadding      = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Top Bar ─────────────────────────────────────────────────────────
            item {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .background(BgCard)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier         = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(OrangeMuted)
                            .border(2.dp, OrangeAccent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val path = profilePhotoPath
                        if (path != null && File(path).exists()) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = "Profile Photo",
                                modifier = Modifier.size(44.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else if (avatarInitials.isNotBlank()) {
                            Text(avatarInitials, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = OrangeAccent)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = OrangeAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(greetingIcon, contentDescription = null, tint = greetingIconColor, modifier = Modifier.size(14.dp))
                            Text(
                                wishText,
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("$dayName", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    val isPro = profile.isPremium || prefs.isPro()
                    if (isPro) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x28FFD700))
                                .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable { onOpenSubscription() }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("👑", fontSize = 12.sp)
                                Text("PRO", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700))
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFFD700), Color(0xFFFF9500))
                                    )
                                )
                                .clickable { onOpenSubscription() }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Star, "Upgrade", tint = Color.Black, modifier = Modifier.size(13.dp))
                                Text("PRO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color       = OrangeAccent,
                            modifier    = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(OrangeMuted)
                                .clickable { vm.refreshData() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Refresh, "Refresh", tint = OrangeAccent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Hero Progress Banner ─────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .shadow(6.dp, RoundedCornerShape(20.dp), spotColor = OrangeAccent.copy(alpha = 0.2f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(OrangeAccent, Color(0xFFFF4500))))
                        .tourTarget("dashboard_progress_ring")
                        .onGloballyPositioned { coords ->
                            targetRects[1] = coords.boundsInRoot()
                        }
                        .padding(20.dp)
                ) {
                    Row(
                        modifier             = Modifier.fillMaxWidth(),
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Today's Progress",
                                fontSize   = 13.sp,
                                color      = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "$doneToday",
                                    fontSize   = 40.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color.White,
                                    lineHeight = 40.sp
                                )
                                Text(
                                    " / $totalHabits habits done",
                                    fontSize = 14.sp,
                                    color    = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            // Progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.3f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animProgress.coerceIn(0f, 1f))
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (rawProgress >= 1f) "All done! Great work!" else "${(rawProgress * 100).toInt()}% complete",
                                fontSize = 12.sp,
                                color    = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        // Arc gauge
                        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val sw = 8.dp.toPx()
                                val r  = size.minDimension / 2f - sw / 2f
                                drawCircle(color = Color.White.copy(alpha = 0.25f), radius = r, style = Stroke(sw))
                                drawArc(
                                    color      = Color.White,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animProgress,
                                    useCenter  = false,
                                    style      = Stroke(sw, cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                "${(animProgress * 100).toInt()}%",
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color.White
                            )
                        }
                    }
                }
            }

            // ── Today's Stats Row ────────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            targetRects[2] = coords.boundsInRoot()
                        }
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashStatCard(Icons.Default.Whatshot, "${stats.currentStreak}", "Day Streak", OrangeAccent, Modifier.weight(1f))
                    DashStatCard(Icons.Default.CheckCircle, "$doneToday", "Done Today", GreenAccent, Modifier.weight(1f))
                    DashStatCard(Icons.Default.Flag, "${goals.size}", "Active Goals", VioletAccent, Modifier.weight(1f))
                }
            }

            // ── Focus Mindset Score Card (Premium Added Feature) ─────────────────
            item {
                FocusScoreCard(
                    stats = stats,
                    todayReelsCount = todayReelsCount,
                    reelLimitActive = isReelEnabled && isAccessEnabled
                )
            }

            // ── Control Hub ──────────────────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Control Hub", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    // Wallpaper Card (Full Width)
                    val wallActive = wallpaperTarget != "NONE"
                    val wallStatusText = if (wallActive) "Active" else "Disabled"
                    val wallStatusColor = if (wallActive) GreenAccent else TextMuted
                    val wallTargetLabel = when (wallpaperTarget) {
                        "HOME" -> "Home Screen"
                        "LOCK" -> "Lock Screen"
                        "NONE" -> "Disabled"
                        else   -> "Home & Lock"
                    }
                    val wallDetail = if (wallActive) {
                        "Target: $wallTargetLabel | Last: ${lastUpdateDate ?: "Never"}"
                    } else "Enable wallpaper grid sync"

                    ServiceHubCard(
                        icon       = Icons.Filled.Wallpaper,
                        iconTint   = VioletAccent,
                        title      = "Wallpaper Generator",
                        statusText = wallStatusText,
                        statusTint = wallStatusColor,
                        detailText = wallDetail,
                        onClick    = onOpenWallpaper,
                        onSettingsClick = { showTargetDialog = true },
                        modifier   = Modifier
                            .fillMaxWidth()
                            .tourTarget("dashboard_wallpaper_card")
                            .onGloballyPositioned { coords ->
                                targetRects[0] = coords.boundsInRoot()
                            }
                    )
                    }
                    Spacer(Modifier.height(12.dp))
                    HomeWidgetsCard(
                        onClick = onOpenWidgets,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            targetRects[3] = coords.boundsInRoot()
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    // ── Nightly Update Behaviour Card ───────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(18.dp), spotColor = CardShadowColor)
                            .clip(RoundedCornerShape(18.dp))
                            .background(BgCard)
                            .border(1.dp, GlassStroke, RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SkyDim)
                                            .border(1.dp, SkyAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.AutoMode, null, tint = SkyAccent, modifier = Modifier.size(18.dp))
                                    }
                                    Column {
                                        Text("Nightly Update Behaviour", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("Choose what refreshes at midnight", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Mode 1: Wallpaper + Widgets
                                val isMode1 = updateMode == UserPrefs.UPDATE_MODE_WALLPAPER_AND_WIDGETS
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isMode1) VioletAccent.copy(alpha = 0.12f) else BgMuted)
                                        .border(1.dp, if (isMode1) VioletAccent.copy(alpha = 0.5f) else GlassStroke, RoundedCornerShape(12.dp))
                                        .clickable {
                                            val m = UserPrefs.UPDATE_MODE_WALLPAPER_AND_WIDGETS
                                            prefs.setWallpaperUpdateMode(m)
                                            updateMode = m
                                            com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(context)
                                            com.consistencygridwallpaper.workers.WallpaperWorker.scheduleImmediate(context)
                                            android.widget.Toast.makeText(context, "Nightly update: Wallpaper + Widgets", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            Icons.Filled.Wallpaper, null,
                                            tint = if (isMode1) VioletAccent else TextMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            "Wallpaper + Widgets",
                                            fontSize = 11.sp,
                                            fontWeight = if (isMode1) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isMode1) VioletAccent else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Mode 2: Widgets Only
                                val isMode2 = updateMode == UserPrefs.UPDATE_MODE_WIDGETS_ONLY
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isMode2) SkyAccent.copy(alpha = 0.12f) else BgMuted)
                                        .border(1.dp, if (isMode2) SkyAccent.copy(alpha = 0.5f) else GlassStroke, RoundedCornerShape(12.dp))
                                        .clickable {
                                            val m = UserPrefs.UPDATE_MODE_WIDGETS_ONLY
                                            prefs.setWallpaperUpdateMode(m)
                                            updateMode = m
                                            com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(context)
                                            android.widget.Toast.makeText(context, "Nightly update: Widgets Only", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            Icons.Filled.Widgets, null,
                                            tint = if (isMode2) SkyAccent else TextMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            "Widgets Only",
                                            fontSize = 11.sp,
                                            fontWeight = if (isMode2) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isMode2) SkyAccent else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Today's Habits ───────────────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Section header
                    Row(
                        modifier             = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment    = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Today's Habits", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                if (totalHabits == 0) "No habits yet"
                                else if (doneToday == totalHabits) "All done!"
                                else "$doneToday of $totalHabits completed",
                                fontSize = 11.sp,
                                color = if (doneToday == totalHabits && totalHabits > 0) GreenAccent else TextMuted
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenHabits() }
                                .background(OrangeDim)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("See all", fontSize = 11.sp, color = OrangeAccent, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ChevronRight, null, tint = OrangeAccent, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (habits.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = CardShadowColor)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BgCard)
                                .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                                .clickable { onOpenHabits() }
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No habits yet", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Tap to add your first habit", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        habits.take(5).forEachIndexed { index, habit ->
                            val isDone = todayLogs[habit.id] == true
                            DashHabitRow(
                                habit    = habit,
                                isDone   = isDone,
                                onToggle = { vm.toggleHabit(habit.id, isDone) }
                            )
                            if (index < minOf(habits.size - 1, 4)) Spacer(Modifier.height(8.dp))
                        }
                        if (habits.size > 5) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgMuted)
                                    .clickable { onOpenHabits() }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+ ${habits.size - 5} more habits",
                                    fontSize = 12.sp,
                                    color = OrangeAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ── Quick Actions ────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .tourTarget("dashboard_quick_actions")
                        .onGloballyPositioned { coords ->
                            targetRects[6] = coords.boundsInRoot()
                        }
                        .padding(horizontal = 20.dp)
                ) {
                    Text("Quick Actions", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(Icons.Default.Assignment, "Habits",   OrangeAccent, Modifier.weight(1f), onClick = onOpenHabits)
                        QuickActionCard(Icons.Default.Flag, "Goals",    VioletAccent, Modifier.weight(1f), onClick = onOpenGoals)
                        QuickActionCard(Icons.Default.Whatshot, "Streaks",  GreenAccent,  Modifier.weight(1f), onClick = onOpenStreaks)
                        QuickActionCard(Icons.Default.Notifications, "Reminders",SkyAccent,    Modifier.weight(1f), onClick = onOpenReminders)
                    }
                }
            }

            // ── Active Goals ─────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            targetRects[5] = coords.boundsInRoot()
                        }
                        .padding(horizontal = 20.dp)
                ) {
                    // Section header
                    Row(
                        modifier             = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment    = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Active Goals", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                if (goals.isEmpty()) "No goals set yet" else "${goals.size} goal${if (goals.size != 1) "s" else ""} in progress",
                                fontSize = 11.sp, color = TextMuted
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenGoals() }
                                .background(VioletDim)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("See all", fontSize = 11.sp, color = VioletAccent, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ChevronRight, null, tint = VioletAccent, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (goals.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = CardShadowColor)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BgCard)
                                .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                                .clickable { onOpenGoals() }
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No goals yet", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Tap to set your first goal", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        goals.take(3).forEachIndexed { index, goal ->
                            DashGoalCard(
                                goal = goal,
                                onTap = { onOpenGoals() }
                            )
                            if (index < minOf(goals.size - 1, 2)) Spacer(Modifier.height(10.dp))
                        }
                        if (goals.size > 3) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgMuted)
                                    .clickable { onOpenGoals() }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+ ${goals.size - 3} more goals",
                                    fontSize = 12.sp,
                                    color = VioletAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTargetDialog) {
            AlertDialog(
                onDismissRequest = { showTargetDialog = false },
                containerColor = BgCard,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text("Wallpaper Target", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        val options = listOf(
                            "BOTH" to "Home & Lock Screens",
                            "HOME" to "Home Screen Only",
                            "LOCK" to "Lock Screen Only",
                            "NONE" to "Widgets Only (Disable Wallpaper)"
                        )
                        options.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        prefs.setWallpaperTarget(value)
                                        wallpaperTarget = value
                                        showTargetDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = wallpaperTarget == value,
                                    onClick = {
                                        prefs.setWallpaperTarget(value)
                                        wallpaperTarget = value
                                        showTargetDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = OrangeAccent)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, color = TextPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTargetDialog = false }) {
                        Text("Close", color = OrangeAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }


        if (showTour) {
            // old per-screen tour disabled — noop
        }
    }
}

// ─── Focus Mindset Score Card (Premium Added Feature) ────────────────────────────────
@Composable
private fun FocusScoreCard(
    stats: DashboardStats,
    todayReelsCount: Int,
    reelLimitActive: Boolean
) {
    val habitsDone = stats.doneToday
    val habitsTotal = stats.totalHabits
    val streak = stats.currentStreak

    // Compute focus score: Habits (max 50) + Streak (max 20) + Reel tracker state/under limit (max 30)
    val habitScore = if (habitsTotal > 0) (habitsDone.toFloat() / habitsTotal * 50f) else 0f
    val reelScore = if (reelLimitActive) {
        if (todayReelsCount <= 30) 30f else (30f - (todayReelsCount - 30) * 1.5f).coerceAtLeast(0f)
    } else 0f
    val streakScore = (streak * 2.5f).coerceAtMost(20f)
    
    val totalScore = (habitScore + reelScore + streakScore).toInt().coerceIn(0, 100)

    val (focusLevel, levelColor) = when {
        totalScore >= 80 -> "Legendary Focus" to GreenAccent
        totalScore >= 55 -> "Moderate Focus" to OrangeAccent
        else -> "Needs Discipline" to RoseAccent
    }

    val progressAnim by animateFloatAsState(
        targetValue = totalScore / 100f,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "focusScoreProg"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, Color(0xFF27272A))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Focus Score",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$totalScore",
                        color = levelColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "/100",
                        color = TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = levelColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = focusLevel,
                        color = levelColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))

            // Beautiful Radial Gauge
            Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 6.dp.toPx()
                    val r = size.minDimension / 2f - sw / 2f
                    drawCircle(color = Color.Gray.copy(alpha = 0.12f), radius = r, style = Stroke(sw))
                    drawArc(
                        color = levelColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progressAnim,
                        useCenter = false,
                        style = Stroke(sw, cap = StrokeCap.Round)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = levelColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ─── Dashboard Stat Card ──────────────────────────────────────────────────────
@Composable
private fun DashStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(14.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── Dashboard Habit Row ──────────────────────────────────────────────────────
@Composable
private fun DashHabitRow(
    habit: HabitEntity,
    isDone: Boolean,
    onToggle: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue   = if (isDone) OrangeMuted else BgCard,
        animationSpec = tween(300),
        label         = "dashHabitBg"
    )
    val borderColor by animateColorAsState(
        targetValue   = if (isDone) OrangeAccent.copy(alpha = 0.4f) else GlassStroke,
        animationSpec = tween(300),
        label         = "dashHabitBorder"
    )
    val checkScale by animateFloatAsState(
        targetValue   = if (isDone) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label         = "dashCheck"
    )

    val habitIcon = remember(habit.title) {
        val t = habit.title.lowercase()
        when {
            t.contains("read") || t.contains("study") || t.contains("learn") || t.contains("book") -> Icons.Default.Book
            t.contains("run") || t.contains("jog") || t.contains("gym") || t.contains("workout") || t.contains("exercise") || t.contains("walk") -> Icons.Default.DirectionsRun
            t.contains("meditat") || t.contains("sleep") || t.contains("rest") || t.contains("relax") -> Icons.Default.Spa
            t.contains("water") || t.contains("drink") || t.contains("hydrate") -> Icons.Default.LocalCafe
            t.contains("code") || t.contains("program") || t.contains("work") || t.contains("computer") -> Icons.Default.Code
            t.contains("diet") || t.contains("eat") || t.contains("food") || t.contains("healthy") -> Icons.Default.Restaurant
            t.contains("journal") || t.contains("write") || t.contains("diary") -> Icons.Default.Edit
            else -> Icons.Default.Star
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon badge
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDone) OrangeAccent.copy(alpha = 0.15f) else BgMuted)
                .border(1.dp, if (isDone) OrangeAccent.copy(alpha = 0.3f) else GlassStroke, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(habitIcon, null, tint = if (isDone) OrangeAccent else TextMuted, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                habit.title,
                fontSize       = 14.sp,
                fontWeight     = FontWeight.SemiBold,
                color          = if (isDone) TextMuted else TextPrimary,
                maxLines       = 1,
                overflow       = TextOverflow.Ellipsis
            )
            Text(
                if (isDone) "Completed today" else if (!habit.scheduledTime.isNullOrBlank()) habit.scheduledTime else "Tap to complete",
                fontSize = 11.sp,
                color    = if (isDone) GreenAccent else TextMuted
            )
        }
        // Animated check circle
        Box(
            modifier = Modifier
                .size(30.dp)
                .scale(checkScale)
                .clip(CircleShape)
                .background(if (isDone) OrangeAccent else Color.Transparent)
                .border(2.dp, if (isDone) OrangeAccent else GlassStroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ─── Quick Action Card ────────────────────────────────────────────────────────
@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(12.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .clickable { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Dashboard Goal Card ──────────────────────────────────────────────────────
@Composable
private fun DashGoalCard(
    goal: GoalEntity,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFrac = (goal.progress / 100f).coerceIn(0f, 1f)
    val animFrac by animateFloatAsState(
        targetValue   = progressFrac,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label         = "goalProg"
    )

    val accentColor = when {
        goal.progress >= 100 -> GreenAccent
        goal.progress >= 60  -> OrangeAccent
        else                 -> VioletAccent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .clickable { onTap() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.12f))
                .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (goal.progress >= 100) {
                Icon(Icons.Filled.CheckCircle, null, tint = GreenAccent, modifier = Modifier.size(22.dp))
            } else {
                Icon(Icons.Filled.Flag, null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                goal.title,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = TextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(BgMuted)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animFrac)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                when {
                                    goal.progress >= 100 -> listOf(GreenAccent, GreenAccent)
                                    goal.progress >= 60  -> listOf(OrangeAccent, Color(0xFFFF4500))
                                    else                 -> listOf(VioletAccent, VioletLight)
                                }
                            )
                        )
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (goal.progress >= 100) "Completed" else "${goal.progress}% complete",
                    fontSize = 11.sp,
                    color = if (goal.progress >= 100) GreenAccent else TextMuted
                )
                Text(
                    "Tap for details",
                    fontSize = 10.sp,
                    color = TextMuted.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Percentage ring
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 4.dp.toPx()
                val r  = size.minDimension / 2f - sw / 2f
                drawCircle(color = Color(0xFFF5F5F5), radius = r, style = Stroke(sw))
                drawArc(
                    color      = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animFrac,
                    useCenter  = false,
                    style      = Stroke(sw, cap = StrokeCap.Round)
                )
            }
            Text(
                "${goal.progress}%",
                fontSize   = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = accentColor
            )
        }
    }
}

// ─── Service Control Hub Card ────────────────────────────────────────────────
@Composable
private fun ServiceHubCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    statusText: String,
    statusTint: Color,
    detailText: String,
    onClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.1f))
                        .border(1.dp, iconTint.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onSettingsClick != null) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable { onSettingsClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = TextMuted.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                }
            }
            
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusTint)
                    )
                    Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusTint)
                }
                Spacer(Modifier.height(4.dp))
                Text(detailText, fontSize = 9.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val DashboardTourSteps = listOf(
    com.consistencygridwallpaper.ui.compose.components.TourStep(
        title = "Wallpaper Studio",
        description = "Build and customize your grid wallpaper with birth dates, themes, quotes, and layouts.",
        icon = androidx.compose.material.icons.Icons.Default.Wallpaper,
        targetIndex = 0
    ),
    com.consistencygridwallpaper.ui.compose.components.TourStep(
        title = "Today's Progress",
        description = "See your habit completion percentage and progress ring updated in real-time.",
        icon = androidx.compose.material.icons.Icons.Default.Assignment,
        targetIndex = 1
    ),
    com.consistencygridwallpaper.ui.compose.components.TourStep(
        title = "Streaks & Stats",
        description = "Keep your daily consistency streak alive and view your habit logs.",
        icon = androidx.compose.material.icons.Icons.Default.Whatshot,
        targetIndex = 2
    ),
    com.consistencygridwallpaper.ui.compose.components.TourStep(
        title = "Interactive Home Widgets",
        description = "Setup premium widget blocks on your Home Screen to view stats at a glance.",
        icon = androidx.compose.material.icons.Icons.Default.Widgets,
        targetIndex = 3
    ),
    com.consistencygridwallpaper.ui.compose.components.TourStep(
        title = "Reel Controller",
        description = "Set screen time limits on Reels (Instagram/YouTube) to reclaim your focus.",
        icon = androidx.compose.material.icons.Icons.Default.StopScreenShare,
        targetIndex = 4
    ),
    com.consistencygridwallpaper.ui.compose.components.TourStep(
        title = "Life Goals",
        description = "Define your main goals, split them into subgoals, and track progress using high-end progress bars.",
        icon = androidx.compose.material.icons.Icons.Default.Flag,
        targetIndex = 5
    ),
    com.consistencygridwallpaper.ui.compose.components.TourStep(
        title = "Quick Actions & Reminders",
        description = "Quickly access habit list, goals, streaks, or add custom reminders.",
        icon = androidx.compose.material.icons.Icons.Default.Notifications,
        targetIndex = 6
    )
)


private fun isAccessibilityEnabled(context: android.content.Context): Boolean {
    return try {
        val services = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        services.contains(context.packageName, ignoreCase = true)
    } catch (e: Exception) { false }
}

@Composable
private fun HomeWidgetsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OrangeAccent.copy(alpha = 0.1f))
                    .border(1.dp, OrangeAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Widgets, null, tint = OrangeAccent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Interactive Home Widgets", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text("Setup premium widget blocks on your Home Screen", fontSize = 10.sp, color = TextMuted)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        }
    }
}
