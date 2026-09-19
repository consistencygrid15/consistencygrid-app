package com.consistencygridwallpaper.ui.compose.goals

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.consistencygridwallpaper.storage.room.GoalEntity
import com.consistencygridwallpaper.ui.compose.components.GlassmorphicCard
import com.consistencygridwallpaper.ui.compose.components.TintCard
import com.consistencygridwallpaper.ui.compose.theme.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import com.consistencygridwallpaper.ui.compose.tour.tourTarget
import com.consistencygridwallpaper.ui.compose.tour.TourManager
import androidx.compose.ui.platform.LocalContext
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.ui.compose.components.TourOverlay
import com.consistencygridwallpaper.ui.compose.components.TourStep

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel = viewModel(),
    onOpenSubscription: () -> Unit = {}
) {
    val activeGoals    by viewModel.activeGoals.collectAsStateWithLifecycle()
    val lifeMilestones by viewModel.lifeMilestones.collectAsStateWithLifecycle()
    val stats          by viewModel.dashboardStats.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    var showTour by remember { mutableStateOf(!prefs.getBoolean("has_shown_goals_tour", false)) }
    var currentTourStep by remember { mutableStateOf(0) }
    val targetRects = remember { mutableStateMapOf<Int, Rect>() }
    val listState = rememberLazyListState()

    LaunchedEffect(currentTourStep, showTour) {
        if (showTour) {
            val scrollIndex = when (currentTourStep) {
                0 -> 1 // MomentumCard
                1 -> 2 // StatsChips
                2 -> 4 // Active Goals
                else -> 0
            }
            listState.scrollToItem(scrollIndex)
        }
    }

    // ── Global tour scroll: ensure target item is visible ─────────────────────
    val globalTourStep = TourManager.currentStepIndex
    LaunchedEffect(globalTourStep) {
        if (TourManager.isActive && TourManager.currentStep?.screenRoute == "goals") {
            val scrollIndex = when (TourManager.currentStep?.targetKey) {
                "goals_momentum_ring" -> 1
                "goals_fab"           -> 0
                else                  -> 0
            }
            listState.animateScrollToItem(scrollIndex)
        }
    }

    val goalsTourSteps = remember(activeGoals.isEmpty() && lifeMilestones.isEmpty()) {
        val hasGoals = activeGoals.isNotEmpty() || lifeMilestones.isNotEmpty()
        if (hasGoals) {
            listOf(
                TourStep("Momentum Tracker", "Visualize your overall goal tracking percentage and progress indicator.", androidx.compose.material.icons.Icons.Default.TrendingUp, 0),
                TourStep("Quick Statistics", "View your total, completed, active goals, and milestones in a single glance.", androidx.compose.material.icons.Icons.Default.Analytics, 1),
                TourStep("Goal Cards", "View your target milestone progress, sub-tasks, categories, and categories.", androidx.compose.material.icons.Icons.Default.Flag, 2),
                TourStep("Add New Goals", "Tap the '+' action button to quickly input a new goal, specify categories, and define sub-tasks.", androidx.compose.material.icons.Icons.Default.Add, 3)
            )
        } else {
            listOf(
                TourStep("Momentum Tracker", "Visualize your overall goal tracking percentage and progress indicator.", androidx.compose.material.icons.Icons.Default.TrendingUp, 0),
                TourStep("Quick Statistics", "View your total, completed, active goals, and milestones in a single glance.", androidx.compose.material.icons.Icons.Default.Analytics, 1),
                TourStep("Add New Goals", "Tap the '+' action button to quickly input a new goal, specify categories, and define sub-tasks.", androidx.compose.material.icons.Icons.Default.Add, 3)
            )
        }
    }

    var showInlineForm    by remember { mutableStateOf(false) }
    var showProLimitSheet by remember { mutableStateOf(false) }
    var editingGoal       by remember { mutableStateOf<GoalEntity?>(null) }

    if (showProLimitSheet) {
        com.consistencygridwallpaper.ui.compose.habits.ProFeatureLimitSheet(
            title = "Goals Limit Reached (3/3)",
            description = "Free accounts can track up to 3 active goals simultaneously. Upgrade to Pro for unlimited goals, custom sub-tasks, and life milestones.",
            featureHighlight = "Pro gives you Unlimited Goals to build your future.",
            onUpgrade = {
                showProLimitSheet = false
                onOpenSubscription()
            },
            onDismiss = { showProLimitSheet = false }
        )
    }

    val fabRotation by animateFloatAsState(
        targetValue   = if (showInlineForm) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label         = "fabRot"
    )

    Scaffold(
        containerColor = BgBase,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(8.dp, CircleShape, spotColor = OrangeAccent.copy(alpha = 0.3f))
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(OrangeAccent, Color(0xFFFF4500))))
                    .tourTarget("goals_fab")
                    .onGloballyPositioned { coords ->
                        targetRects[3] = coords.boundsInRoot()
                    }
                    .clickable {
                        if (showInlineForm && editingGoal == null) {
                            showInlineForm = false
                        } else {
                            val isPro = prefs.isPro()
                            if (!isPro && activeGoals.size >= 3) {
                                showProLimitSheet = true
                            } else {
                                editingGoal = null
                                showInlineForm = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add, "Add Goal",
                    tint     = Color.White,
                    modifier = Modifier.size(24.dp).rotate(fabRotation)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state          = listState,
                modifier       = Modifier.fillMaxSize().padding(padding).background(BgBase),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp)
                        .background(BgCard)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment    = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(OrangeMuted),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Flag, null, tint = OrangeAccent, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Goals & Vision", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                Text("Track your long-term objectives", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // ── Momentum Ring ──────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                MomentumCard(
                    stats    = stats,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .tourTarget("goals_momentum_ring")
                        .onGloballyPositioned { coords ->
                            targetRects[0] = coords.boundsInRoot()
                        }
                )
            }

            // ── Stats Chips ────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned { coords ->
                            targetRects[1] = coords.boundsInRoot()
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GoalStatChip(Modifier.weight(1f), "Total",      stats.totalGoals.toString(),      OrangeAccent)
                    GoalStatChip(Modifier.weight(1f), "Done",       stats.completedGoals.toString(),  GreenAccent)
                    GoalStatChip(Modifier.weight(1f), "Active",     stats.inProgressGoals.toString(), VioletAccent)
                    GoalStatChip(Modifier.weight(1f), "Milestones", stats.lifeMilestonesCount.toString(), GoldAccent)
                }
            }

            // ── Inline Creation Form Expandable ────────────────────────────
            item {
                AnimatedVisibility(
                    visible = showInlineForm,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        InlineManageGoalForm(
                            goalToEdit = editingGoal,
                            viewModel = viewModel,
                            onDismiss = {
                                showInlineForm = false
                                editingGoal = null
                            }
                        )
                    }
                }
            }

            // ── Active Goals section label ─────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                SectionLabel(
                    title  = "Active Goals",
                    count  = "${activeGoals.size} goals",
                    accent = OrangeAccent
                )
            }

            if (activeGoals.isEmpty()) {
                item {
                    EmptyStateBox(emoji = "🎯", title = "No active goals yet", sub = "Tap + to set your first goal")
                }
            } else {
                items(activeGoals, key = { "active_${it.id}" }) { goal ->
                    val isFirst = activeGoals.firstOrNull()?.id == goal.id
                    GoalCard(
                        goal      = goal,
                        viewModel = viewModel,
                        onClick   = { editingGoal = goal; showInlineForm = true },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .then(
                                if (isFirst) {
                                    Modifier.onGloballyPositioned { coords ->
                                        targetRects[2] = coords.boundsInRoot()
                                    }
                                } else Modifier
                            )
                    )
                }
            }

            // ── Life Milestones ────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                SectionLabel(
                    title  = "Life Milestones",
                    count  = "${lifeMilestones.size} items",
                    accent = GoldAccent
                )
            }

            if (lifeMilestones.isEmpty()) {
                item {
                    EmptyStateBox(emoji = "⭐", title = "No life milestones yet", sub = "Dream big — add a milestone!")
                }
            } else {
                items(lifeMilestones, key = { "mile_${it.id}" }) { goal ->
                    val isFirst = lifeMilestones.firstOrNull()?.id == goal.id && activeGoals.isEmpty()
                    GoalCard(
                        goal      = goal,
                        viewModel = viewModel,
                        onClick   = { editingGoal = goal; showInlineForm = true },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .then(
                                if (isFirst) {
                                    Modifier.onGloballyPositioned { coords ->
                                        targetRects[2] = coords.boundsInRoot()
                                    }
                                } else Modifier
                            )
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
            }

            if (showTour) {
                // old per-screen tour disabled — global tour now used
            }
        }
    }
}

// ─── Section Label ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(title: String, count: String, accent: Color) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(accent))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Text(count, fontSize = 12.sp, color = TextMuted)
    }
}

// ─── Empty State ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyStateBox(emoji: String, title: String, sub: String) {
    Box(
        modifier         = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, fontSize = 40.sp)
            Text(title, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(sub,   color = TextMuted,     fontSize = 12.sp)
        }
    }
}

// ─── Momentum Card ─────────────────────────────────────────────────────────────
@Composable
private fun MomentumCard(stats: GoalsViewModel.GoalsDashboardStats, modifier: Modifier) {
    val animArc by animateFloatAsState(
        targetValue   = stats.overallMomentumPercentage / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label         = "momentumArc"
    )

    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = OrangeAccent.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Momentum ring
            Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val sw = 12.dp.toPx()
                    val r  = size.minDimension / 2f - sw / 2f
                    drawCircle(color = BgMuted, radius = r, style = Stroke(sw))
                    drawArc(
                        brush      = Brush.sweepGradient(listOf(OrangeAccent, Color(0xFFFF4500), OrangeAccent)),
                        startAngle = -90f,
                        sweepAngle = 360f * animArc,
                        useCenter  = false,
                        style      = Stroke(sw, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.overallMomentumPercentage}%",
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = OrangeAccent
                    )
                    Text("on track", fontSize = 10.sp, color = TextMuted)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Life Momentum", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "You've achieved ${stats.completedGoals} of ${stats.totalGoals} goals.",
                fontSize  = 13.sp,
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalBadge("⚡ ${stats.onTrackGoalsCount} ON TRACK", GreenAccent)
                GoalBadge("🎯 ${stats.inProgressGoals} ACTIVE",     OrangeAccent)
                if (stats.lifeMilestonesCount > 0)
                    GoalBadge("⭐ ${stats.lifeMilestonesCount} MILESTONES", GoldAccent)
            }
        }
    }
}

@Composable
fun GoalBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
private fun GoalStatChip(modifier: Modifier, label: String, value: String, accent: Color) {
    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(12.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(label, fontSize = 9.sp,  color = TextMuted,  textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun Badge(text: String, color: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ─── Goal Card ─────────────────────────────────────────────────────────────────
@Composable
private fun GoalCard(
    goal: GoalEntity,
    viewModel: GoalsViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction  = (goal.progress / 100f).coerceIn(0f, 1f)
    val progressPercent   = goal.progress.coerceIn(0, 100)
    val isCompleted       = goal.isCompleted || progressPercent == 100

    val subGoals          = remember(goal.subGoalsJson) { viewModel.parseSubGoals(goal.subGoalsJson) }
    val completedSubGoals = subGoals.count { it.isCompleted }

    val categoryColor = when (goal.category.lowercase()) {
        "health"         -> Color(0xFFEF4444)
        "wealth"         -> GreenAccent
        "mind"           -> VioletAccent
        "work"           -> SkyAccent
        "life milestone" -> GoldAccent
        else             -> OrangeAccent
    }

    val categoryEmoji = when (goal.category.lowercase()) {
        "health"         -> "💪"
        "wealth"         -> "💰"
        "mind"           -> "🧠"
        "work"           -> "💼"
        "life milestone" -> "⭐"
        else             -> "🎯"
    }

    val animProgress by animateFloatAsState(
        targetValue   = progressFraction,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label         = "goalProgress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier             = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment    = Alignment.Top
        ) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Category emoji box
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor.copy(alpha = 0.1f))
                        .border(1.dp, categoryColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(categoryEmoji, fontSize = 20.sp)
                }
                Column {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            goal.category.uppercase(),
                            fontSize      = 9.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = categoryColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text       = goal.title,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary
                    )
                }
            }
            if (goal.isPinned) {
                Icon(Icons.Filled.PushPin, "Pinned", tint = GoldAccent, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // Status badge
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isCompleted) {
                Icon(Icons.Filled.CheckCircle, null, tint = GreenAccent, modifier = Modifier.size(14.dp))
                Text("Completed", fontSize = 11.sp, color = GreenAccent, fontWeight = FontWeight.SemiBold)
            } else {
                Box(Modifier.size(7.dp).clip(CircleShape).background(OrangeAccent))
                Text("In Progress", fontSize = 11.sp, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Progress bar
        Row(
            modifier             = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment    = Alignment.CenterVertically
        ) {
            Text("Progress", fontSize = 11.sp, color = TextMuted)
            Text("$progressPercent%", fontSize = 12.sp, color = categoryColor, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(BgMuted)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animProgress)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(categoryColor, categoryColor.copy(alpha = 0.7f))))
            )
        }

        Spacer(Modifier.height(10.dp))
        Text("$completedSubGoals / ${subGoals.size} steps completed", fontSize = 11.sp, color = TextMuted)

        // Sub-goals
        if (subGoals.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = GlassStroke)
            Spacer(Modifier.height(10.dp))
            Text("STEPS", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = categoryColor, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            subGoals.take(4).forEach { sg ->
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleSubGoal(goal, sg.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (sg.isCompleted) categoryColor.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.5.dp, if (sg.isCompleted) categoryColor else GlassStroke, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (sg.isCompleted) Icon(Icons.Filled.Check, null, tint = categoryColor, modifier = Modifier.size(10.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text           = sg.title,
                        color          = if (sg.isCompleted) TextMuted else TextPrimary,
                        fontSize       = 13.sp,
                        textDecoration = if (sg.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                }
            }
            if (subGoals.size > 4) {
                Text("+${subGoals.size - 4} more steps", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String, accentColor: Color) {
    GlassmorphicCard(modifier = modifier, accentColor = accentColor) {
        Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = accentColor, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
    }
}
