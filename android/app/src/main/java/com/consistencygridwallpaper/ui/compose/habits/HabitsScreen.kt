package com.consistencygridwallpaper.ui.compose.habits

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.consistencygridwallpaper.storage.room.HabitEntity
import com.consistencygridwallpaper.ui.compose.theme.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import com.consistencygridwallpaper.ui.compose.tour.tourTarget
import com.consistencygridwallpaper.ui.compose.tour.TourManager
import androidx.compose.ui.platform.LocalContext
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.ui.compose.components.TourOverlay
import com.consistencygridwallpaper.ui.compose.components.TourStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    vm: HabitsViewModel = viewModel(),
    onOpenSubscription: () -> Unit = {}
) {
    val habits       by vm.habits.collectAsStateWithLifecycle()
    val todayLogs    by vm.todayLogs.collectAsStateWithLifecycle()
    val heatmapData  by vm.heatmapData.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val isSaving     by vm.isSaving.collectAsStateWithLifecycle()
    val saveError    by vm.saveError.collectAsStateWithLifecycle()
    val haptic       = LocalHapticFeedback.current

    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    var showTour by remember { mutableStateOf(!prefs.getBoolean("has_shown_habits_tour", false)) }
    var currentTourStep by remember { mutableStateOf(0) }
    val targetRects = remember { mutableStateMapOf<Int, Rect>() }
    val listState = rememberLazyListState()

    LaunchedEffect(currentTourStep, showTour) {
        if (showTour && (currentTourStep == 2 || currentTourStep == 3)) {
            listState.scrollToItem(0)
        }
    }

    // ── Global tour scroll: ensure target item is visible ─────────────────────
    val globalTourStep = TourManager.currentStepIndex
    LaunchedEffect(globalTourStep) {
        if (TourManager.isActive && TourManager.currentStep?.screenRoute == "habits") {
            listState.animateScrollToItem(0)
        }
    }

    val habitsTourSteps = remember(habits.isEmpty()) {
        if (habits.isEmpty()) {
            listOf(
                TourStep("Stats Dashboard", "Check your today's completion rates, best streak, and consistency score.", androidx.compose.material.icons.Icons.Default.ShowChart, 0),
                TourStep("Create Habit", "Tap the '+' button to schedule and create your first custom habit.", androidx.compose.material.icons.Icons.Default.Add, 1)
            )
        } else {
            listOf(
                TourStep("Stats Dashboard", "Check your today's completion rates, best streak, and consistency score.", androidx.compose.material.icons.Icons.Default.ShowChart, 0),
                TourStep("Create Habit", "Tap the '+' button to schedule and create your first custom habit.", androidx.compose.material.icons.Icons.Default.Add, 1),
                TourStep("Complete Habits", "Tap anywhere on the habit card to mark it as completed for today.", androidx.compose.material.icons.Icons.Default.CheckCircle, 2),
                TourStep("Consistency Heatmap", "Visualize your daily consistency over the last 30 days. Stay Orange!", androidx.compose.material.icons.Icons.Default.GridOn, 3)
            )
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var showAddSheet      by remember { mutableStateOf(false) }
    var showProLimitSheet by remember { mutableStateOf(false) }
    var editingHabit      by remember { mutableStateOf<HabitEntity?>(null) }
    var newHabitTitle     by remember { mutableStateOf("") }
    var newHabitTime      by remember { mutableStateOf("") }

    fun checkAndOpenAddHabit() {
        val isPro = prefs.isPro()
        val activeHabitCount = habits.count { it.isActive && !it.isDeleted }
        if (!isPro && activeHabitCount >= 3) {
            showProLimitSheet = true
        } else {
            showAddSheet = true
        }
    }

    LaunchedEffect(saveError) {
        val msg = saveError ?: return@LaunchedEffect
        scope.launch {
            snackbarHostState.showSnackbar(message = msg, actionLabel = "OK", duration = SnackbarDuration.Long)
            vm.clearSaveError()
        }
    }

    val doneCount  = habits.count { todayLogs[it.id] == true }
    val totalCount = habits.size
    val progress   = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f

    val animArc by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label         = "arc"
    )

    if (showProLimitSheet) {
        ProFeatureLimitSheet(
            title = "Habits Limit Reached (3/3)",
            description = "Free accounts can track up to 3 active habits simultaneously. Upgrade to Pro for unlimited habits, all themes, and custom photo backgrounds.",
            featureHighlight = "Pro gives you Unlimited Habits with zero restrictions.",
            onUpgrade = {
                showProLimitSheet = false
                onOpenSubscription()
            },
            onDismiss = { showProLimitSheet = false }
        )
    }

    if (showAddSheet) {
        AddHabitSheet(
            title    = newHabitTitle,
            time     = newHabitTime,
            isSaving = isSaving,
            onTitle  = { newHabitTitle = it },
            onTime   = { newHabitTime = it },
            onSave   = {
                if (newHabitTitle.isNotBlank()) {
                    vm.addHabit(newHabitTitle.trim(), newHabitTime.takeIf { it.isNotBlank() })
                    newHabitTitle = ""
                    newHabitTime  = ""
                    showAddSheet  = false
                }
            },
            onDismiss = { showAddSheet = false }
        )
    }

    if (editingHabit != null) {
        ManageHabitSheet(
            habit    = editingHabit!!,
            isSaving = isSaving,
            onSave   = { title, time ->
                vm.updateHabitTitle(editingHabit!!.id, title, time)
                editingHabit = null
            },
            onDelete = {
                vm.deleteHabit(editingHabit!!.id)
                editingHabit = null
            },
            onDismiss = { editingHabit = null }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData    = data,
                    containerColor  = TextPrimary,
                    contentColor    = Color.White,
                    actionColor     = OrangeAccent,
                    shape           = RoundedCornerShape(12.dp)
                )
            }
        },
        containerColor = BgBase
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgBase)
            ) {
            // ── Header ─────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp)
                    .background(BgCard)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column {
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "My Habits",
                                fontSize   = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = TextPrimary
                            )
                            Text(
                                "Master your routine. Own your time.",
                                fontSize = 12.sp,
                                color    = TextMuted
                            )
                        }
                        if (isRefreshing) {
                            CircularProgressIndicator(color = OrangeAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { vm.refresh() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Refresh, "Refresh", tint = TextMuted, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(OrangeAccent, Color(0xFFFF4500))))
                                .tourTarget("habits_add_button")
                                .onGloballyPositioned { coords ->
                                    targetRects[1] = coords.boundsInRoot()
                                }
                                .clickable { checkAndOpenAddHabit() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, "Add", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    val isCloudConnected = remember(prefs.getToken()) { !prefs.getToken().isNullOrBlank() }
                    if (!isCloudConnected) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(Intent(context, com.consistencygridwallpaper.auth.AuthActivity::class.java))
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = OrangeAccent.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = OrangeAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Local Mode: Sign in to sync with cloud database",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Sign In",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OrangeAccent
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Stats Dashboard Layout
                    val overallStrength = remember(habits, heatmapData) {
                        val totalBoxes = habits.size * 30
                        if (totalBoxes > 0) {
                            val doneBoxes = habits.sumOf { (heatmapData[it.id] ?: emptyList()).count { done -> done } }
                            (doneBoxes * 100) / totalBoxes
                        } else 0
                    }

                    val maxStreak = remember(habits, heatmapData) {
                        habits.maxOfOrNull { calculateStreak(heatmapData[it.id] ?: emptyList()) } ?: 0
                    }

                    Surface(
                        color = BgMuted,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourTarget("habits_stats_header")
                            .onGloballyPositioned { coords ->
                                targetRects[0] = coords.boundsInRoot()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular Progress Arc
                            Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    val sw = 6.dp.toPx()
                                    val r  = size.minDimension / 2f - sw / 2f
                                    drawCircle(color = Color.LightGray.copy(alpha = 0.2f), radius = r, style = Stroke(sw))
                                    drawArc(
                                        brush      = Brush.sweepGradient(listOf(OrangeAccent, Color(0xFFFF4500), OrangeAccent)),
                                        startAngle = -90f,
                                        sweepAngle = 360f * animArc,
                                        useCenter  = false,
                                        style      = Stroke(sw, cap = StrokeCap.Round)
                                    )
                                }
                                Text(
                                    "${(animArc * 100).toInt()}%",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = OrangeAccent
                                )
                            }
                            Spacer(Modifier.width(14.dp))

                            // Stats Columns
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("TODAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                                    Text("$doneCount/$totalCount", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("BEST STREAK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                                    Text("🔥 $maxStreak d", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = OrangeAccent)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("STRENGTH", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                                    Text("📈 $overallStrength%", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Motivational quote
                    val quote = remember(progress, totalCount) {
                        when {
                            totalCount == 0 -> "Add a habit above to begin your visual streak journey!"
                            progress == 0f -> "Let's kickstart today. Tap a habit to complete!"
                            progress < 0.5f -> "Off to a good start! Small steps lead to big habits."
                            progress < 1.0f -> "More than halfway there! Keep consistency alive."
                            else -> "Perfect day! You are mastering your schedule. 🏆"
                        }
                    }

                    Text(
                        text = quote,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (progress == 1f && totalCount > 0) GreenAccent else TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // ── Habit List ─────────────────────────────────────────────────────
            if (habits.isEmpty() && !isRefreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📋", fontSize = 60.sp)
                        Text("No habits yet", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Build your foundation, one habit at a time", color = TextMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.horizontalGradient(listOf(OrangeAccent, Color(0xFFFF4500))))
                                .clickable { checkAndOpenAddHabit() }
                                .padding(horizontal = 28.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment    = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Add First Habit", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = { vm.refresh() },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = OrangeAccent),
                            border  = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync from server")
                        }
                    }
                }
            } else {
                LazyColumn(
                    state               = listState,
                    modifier            = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(habits, key = { it.id }) { habit ->
                        val isDone  = todayLogs[habit.id] == true
                        val heatmap = heatmapData[habit.id] ?: List(30) { false }
                        val isFirst = habits.firstOrNull()?.id == habit.id
                        HabitCard(
                            habit    = habit,
                            isDone   = isDone,
                            heatmap  = heatmap,
                            onToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.toggleHabit(habit.id, isDone)
                            },
                            onEdit = {
                                editingHabit = habit
                            },
                            modifier = if (isFirst) {
                                Modifier.onGloballyPositioned { coords ->
                                    targetRects[2] = coords.boundsInRoot()
                                }
                            } else Modifier,
                            heatmapModifier = if (isFirst) {
                                Modifier.onGloballyPositioned { coords ->
                                    targetRects[3] = coords.boundsInRoot()
                                }
                            } else Modifier
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            }

            if (showTour) {
                // old per-screen tour disabled — global tour now used
            }
        }
    }
}

// ── Habit Card ─────────────────────────────────────────────────────────────────
@Composable
private fun HabitCard(
    habit: HabitEntity,
    isDone: Boolean,
    heatmap: List<Boolean>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    heatmapModifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue   = if (isDone) OrangeAccent.copy(alpha = 0.5f) else GlassStroke,
        animationSpec = tween(300),
        label         = "border"
    )
    val bgColor by animateColorAsState(
        targetValue   = if (isDone) OrangeMuted else BgCard,
        animationSpec = tween(300),
        label         = "bg"
    )
    val checkScale by animateFloatAsState(
        targetValue   = if (isDone) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label         = "checkScale"
    )

    val habitEmoji = remember(habit.title) {
        val t = habit.title.lowercase()
        when {
            t.contains("read")                                         -> "📚"
            t.contains("run") || t.contains("jog")                    -> "🏃"
            t.contains("gym") || t.contains("workout") || t.contains("exercise") -> "💪"
            t.contains("meditat")                                      -> "🧘"
            t.contains("water") || t.contains("drink")                -> "💧"
            t.contains("sleep") || t.contains("rest")                 -> "😴"
            t.contains("study") || t.contains("learn")                -> "🎓"
            t.contains("code") || t.contains("program")               -> "💻"
            t.contains("diet") || t.contains("eat")                   -> "🥗"
            t.contains("walk")                                         -> "🚶"
            t.contains("journal") || t.contains("write")              -> "✍️"
            else                                                       -> "⭐"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Emoji icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDone) OrangeAccent.copy(alpha = 0.15f) else BgMuted)
                    .border(1.dp, if (isDone) OrangeAccent.copy(alpha = 0.35f) else GlassStroke, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(habitEmoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text           = habit.title,
                    fontSize       = 15.sp,
                    fontWeight     = FontWeight.SemiBold,
                    color          = if (isDone) TextMuted else TextPrimary,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (!habit.scheduledTime.isNullOrBlank()) {
                        Row(
                            verticalAlignment    = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Filled.AccessTime, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                            Text(habit.scheduledTime, fontSize = 11.sp, color = TextMuted)
                        }
                    } else {
                        Text(
                            if (isDone) "Completed today ✓" else "Tap to mark done",
                            fontSize = 11.sp,
                            color    = if (isDone) GreenAccent else TextMuted
                        )
                    }

                    // Streaks indicator
                    val streak = calculateStreak(heatmap)
                    if (streak > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(OrangeDim)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Filled.Whatshot,
                                null,
                                tint = OrangeAccent,
                                modifier = Modifier.size(10.dp)
                            )
                            Text("$streak d", fontSize = 10.sp, color = OrangeAccent, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Completion strength
                    val completedDays = heatmap.count { it }
                    val rate = if (heatmap.isNotEmpty()) (completedDays * 100) / heatmap.size else 0
                    if (rate > 0) {
                        Text(
                            text = "📈 $rate% str",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Edit button
            IconButton(
                onClick = { onEdit() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Edit,
                    "Edit",
                    tint = TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Check circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .scale(checkScale)
                    .clip(CircleShape)
                    .background(if (isDone) OrangeAccent else Color.Transparent)
                    .border(2.dp, if (isDone) OrangeAccent else GlassStroke, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ── 30-day heatmap ─────────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        Row(
            modifier              = heatmapModifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            heatmap.forEachIndexed { i, done ->
                val isToday = i == heatmap.lastIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                isToday && done -> OrangeAccent
                                isToday        -> OrangeAccent.copy(alpha = 0.25f)
                                done           -> OrangeAccent.copy(alpha = 0.6f)
                                else           -> BgMuted
                            }
                        )
                )
            }
        }
        Row(
            modifier              = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("30 days ago", fontSize = 8.sp, color = TextMuted)
            Text("Today", fontSize = 8.sp, color = if (heatmap.lastOrNull() == true) OrangeAccent else TextMuted)
        }
    }
}

// ── Add Habit Sheet ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHabitSheet(
    title: String,
    time: String,
    isSaving: Boolean,
    onTitle: (String) -> Unit,
    onTime: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle       = { BottomSheetDefaults.DragHandle(color = OrangeAccent.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(OrangeMuted)
                        .border(1.dp, OrangeAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, null, tint = OrangeAccent, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("New Habit", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("What do you want to track?", fontSize = 12.sp, color = TextMuted)
                }
            }

            HorizontalDivider(color = GlassStroke)

            // Habit name input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("HABIT NAME", fontSize = 10.sp, color = OrangeAccent, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                OutlinedTextField(
                    value           = title,
                    onValueChange   = onTitle,
                    modifier        = Modifier.fillMaxWidth(),
                    placeholder     = { Text("e.g. Read for 30 minutes", color = TextMuted, fontSize = 14.sp) },
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = OrangeAccent,
                        unfocusedBorderColor    = GlassStroke,
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary,
                        cursorColor             = OrangeAccent,
                        focusedContainerColor   = BgCard,
                        unfocusedContainerColor = BgMuted
                    ),
                    shape      = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = OrangeAccent.copy(alpha = 0.7f)) }
                )
            }

            // Reminder time input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("REMINDER TIME (OPTIONAL)", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                OutlinedTextField(
                    value           = time,
                    onValueChange   = onTime,
                    modifier        = Modifier.fillMaxWidth(),
                    placeholder     = { Text("e.g. 08:00 AM", color = TextMuted, fontSize = 14.sp) },
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = OrangeAccent,
                        unfocusedBorderColor    = GlassStroke,
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary,
                        cursorColor             = OrangeAccent,
                        focusedContainerColor   = BgCard,
                        unfocusedContainerColor = BgMuted
                    ),
                    shape      = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.AccessTime, null, tint = TextMuted) }
                )
            }

            // Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (title.isNotBlank() && !isSaving)
                            Brush.horizontalGradient(listOf(OrangeAccent, Color(0xFFFF4500)))
                        else
                            Brush.horizontalGradient(listOf(BgMuted, BgMuted))
                    )
                    .clickable(enabled = title.isNotBlank() && !isSaving) { onSave() },
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Saving…", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Text(
                        if (title.isNotBlank()) "Save Habit ✓" else "Enter a habit name",
                        color      = if (title.isNotBlank()) Color.White else TextMuted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 15.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageHabitSheet(
    habit: HabitEntity,
    isSaving: Boolean,
    onSave: (String, String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(habit.title) }
    var time by remember { mutableStateOf(habit.scheduledTime ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle       = { BottomSheetDefaults.DragHandle(color = OrangeAccent.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(OrangeMuted)
                        .border(1.dp, OrangeAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Edit, null, tint = OrangeAccent, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Edit Habit", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("Refine your routine details", fontSize = 12.sp, color = TextMuted)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Delete", tint = RoseAccent)
                }
            }

            HorizontalDivider(color = GlassStroke)

            // Habit name input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("HABIT NAME", fontSize = 10.sp, color = OrangeAccent, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                OutlinedTextField(
                    value           = title,
                    onValueChange   = { title = it },
                    modifier        = Modifier.fillMaxWidth(),
                    placeholder     = { Text("e.g. Read for 30 minutes", color = TextMuted, fontSize = 14.sp) },
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = OrangeAccent,
                        unfocusedBorderColor    = GlassStroke,
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary,
                        cursorColor             = OrangeAccent,
                        focusedContainerColor   = BgCard,
                        unfocusedContainerColor = BgMuted
                    ),
                    shape      = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = OrangeAccent.copy(alpha = 0.7f)) }
                )
            }

            // Reminder time input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("REMINDER TIME (OPTIONAL)", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                OutlinedTextField(
                    value           = time,
                    onValueChange   = { time = it },
                    modifier        = Modifier.fillMaxWidth(),
                    placeholder     = { Text("e.g. 08:00 AM", color = TextMuted, fontSize = 14.sp) },
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = OrangeAccent,
                        unfocusedBorderColor    = GlassStroke,
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary,
                        cursorColor             = OrangeAccent,
                        focusedContainerColor   = BgCard,
                        unfocusedContainerColor = BgMuted
                    ),
                    shape      = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.AccessTime, null, tint = TextMuted) }
                )
            }

            // Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (title.isNotBlank() && !isSaving)
                            Brush.horizontalGradient(listOf(OrangeAccent, Color(0xFFFF4500)))
                        else
                            Brush.horizontalGradient(listOf(BgMuted, BgMuted))
                    )
                    .clickable(enabled = title.isNotBlank() && !isSaving) { onSave(title, time) },
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Saving…", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Text(
                        if (title.isNotBlank()) "Save Habit ✓" else "Enter a habit name",
                        color      = if (title.isNotBlank()) Color.White else TextMuted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 15.sp
                    )
                }
            }
        }
    }
}

private fun calculateStreak(heatmap: List<Boolean>): Int {
    if (heatmap.isEmpty()) return 0
    var streak = 0
    val lastIdx = heatmap.lastIndex
    val todayDone = heatmap[lastIdx]
    
    if (todayDone) {
        for (i in lastIdx downTo 0) {
            if (heatmap[i]) streak++ else break
        }
    } else {
        if (lastIdx > 0 && heatmap[lastIdx - 1]) {
            for (i in (lastIdx - 1) downTo 0) {
                if (heatmap[i]) streak++ else break
            }
        }
    }
    return streak
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProFeatureLimitSheet(
    title: String,
    description: String,
    featureHighlight: String,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141B),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFFFFD700).copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0x28FFD700))
                    .border(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("👑", fontSize = 32.sp)
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Text(
                text = description,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )

            Surface(
                color = Color(0xFF1F1F2C),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("✨", fontSize = 16.sp)
                    Text(
                        featureHighlight,
                        color = Color(0xFFFFD700),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Upgrade button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFFD700), Color(0xFFFF9500))
                        )
                    )
                    .clickable { onUpgrade() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Upgrade to Pro",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }

            TextButton(onClick = onDismiss) {
                Text("Maybe Later", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
    }
}
