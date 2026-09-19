package com.consistencygridwallpaper.ui.compose.reminders

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
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
import com.consistencygridwallpaper.storage.room.ReminderEntity
import com.consistencygridwallpaper.ui.compose.theme.*
import java.text.SimpleDateFormat
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import com.consistencygridwallpaper.ui.compose.tour.tourTarget
import com.consistencygridwallpaper.ui.compose.tour.TourManager
import androidx.compose.ui.platform.LocalContext
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.ui.compose.components.TourOverlay
import com.consistencygridwallpaper.ui.compose.components.TourStep
import java.util.*
import androidx.compose.foundation.lazy.rememberLazyListState

@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenSubscription: () -> Unit = {}
) {
    val reminders by viewModel.uncompletedReminders.collectAsState()
    val completedReminders by viewModel.completedReminders.collectAsState()

    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    var showTour by remember { mutableStateOf(false) } // old tour disabled
    var currentTourStep by remember { mutableStateOf(0) }
    val targetRects = remember { mutableStateMapOf<Int, Rect>() }
    val listState = rememberLazyListState()

    LaunchedEffect(currentTourStep, showTour) {
        if (showTour) {
            val scrollIndex = when (currentTourStep) {
                0 -> 1 // ReminderSummaryCard
                1 -> 2 // Filter Tabs Row
                2 -> 4 // First reminder items group (starts at index 4)
                else -> 0
            }
            listState.scrollToItem(scrollIndex)
        }
    }

    // ── Global tour scroll: ensure target item is visible ─────────────────────
    val globalTourStep = TourManager.currentStepIndex
    LaunchedEffect(globalTourStep) {
        if (TourManager.isActive && TourManager.currentStep?.screenRoute == "reminders") {
            val scrollIndex = when (TourManager.currentStep?.targetKey) {
                "reminders_summary_card" -> 1
                "reminders_fab"          -> 0
                else                     -> 0
            }
            listState.animateScrollToItem(scrollIndex)
        }
    }

    val remindersTourSteps = remember(reminders.isEmpty()) {
        if (reminders.isNotEmpty()) {
            listOf(
                TourStep("Schedule Summary", "View your schedule completion percentage, active counts, and urgent priority badges.", androidx.compose.material.icons.Icons.Default.Schedule, 0),
                TourStep("Quick Filters", "Toggle view between all tasks, due today, or high priority items.", androidx.compose.material.icons.Icons.Default.FilterList, 1),
                TourStep("Reminder Items", "Double tap cards to edit description, start/end dates, priorities, or swipe/tap checkmark to complete.", androidx.compose.material.icons.Icons.Default.AssignmentTurnedIn, 2),
                TourStep("Add Reminder", "Tap the '+' button to schedule reminders, choose priorities, and select marker colors.", androidx.compose.material.icons.Icons.Default.Add, 3)
            )
        } else {
            listOf(
                TourStep("Schedule Summary", "View your schedule completion percentage, active counts, and urgent priority badges.", androidx.compose.material.icons.Icons.Default.Schedule, 0),
                TourStep("Quick Filters", "Toggle view between all tasks, due today, or high priority items.", androidx.compose.material.icons.Icons.Default.FilterList, 1),
                TourStep("Add Reminder", "Tap the '+' button to schedule reminders, choose priorities, and select marker colors.", androidx.compose.material.icons.Icons.Default.Add, 3)
            )
        }
    }

    var showInlineForm       by remember { mutableStateOf(false) }
    var showProLimitSheet   by remember { mutableStateOf(false) }
    var showCompletedSection by remember { mutableStateOf(false) }
    var selectedReminder     by remember { mutableStateOf<ReminderEntity?>(null) }
    var selectedFilter       by remember { mutableStateOf("All") } // "All", "Today", "High"

    if (showProLimitSheet) {
        com.consistencygridwallpaper.ui.compose.habits.ProFeatureLimitSheet(
            title = "Reminders Limit Reached (3/3)",
            description = "Free accounts can track up to 3 active reminders simultaneously. Upgrade to Pro for unlimited reminders, notifications, and custom priority alerts.",
            featureHighlight = "Pro gives you Unlimited Reminders to keep you on schedule.",
            onUpgrade = {
                showProLimitSheet = false
                onOpenSubscription()
            },
            onDismiss = { showProLimitSheet = false }
        )
    }

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today   = remember { dateFmt.format(Date()) }

    // Statistics calculations
    val totalCount     = reminders.size
    val todayList      = remember(reminders, today) { reminders.filter { it.startDate == today || (it.startDate <= today && it.endDate >= today) } }
    val todayCount     = todayList.size
    val highPriority   = remember(reminders) { reminders.filter { it.priority == 2 } }
    val highCount      = highPriority.size

    val filteredReminders = remember(reminders, selectedFilter, today) {
        when (selectedFilter) {
            "Today" -> todayList
            "High"  -> highPriority
            else    -> reminders
        }
    }

    val grouped = remember(filteredReminders, today) {
        val todayL    = mutableListOf<ReminderEntity>()
        val upcomingL = mutableListOf<ReminderEntity>()
        val pastL     = mutableListOf<ReminderEntity>()
        filteredReminders.forEach { r ->
            when {
                r.startDate == today || (r.startDate <= today && r.endDate >= today) -> todayL.add(r)
                r.startDate > today  -> upcomingL.add(r)
                else                 -> pastL.add(r)
            }
        }
        listOf(
            "Today"    to todayL,
            "Upcoming" to upcomingL,
            "Past"     to pastL
        ).filter { it.second.isNotEmpty() }
    }

    val fabRotation by animateFloatAsState(
        targetValue   = if (showInlineForm) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label         = "fabRot"
    )

    Box(modifier = Modifier.fillMaxSize().background(BgBase)) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // ── 1. Header Bar ───────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp)
                        .background(BgCard)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(OrangeMuted)
                                    .border(1.dp, OrangeAccent.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Notifications, null, tint = OrangeAccent, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("Schedule & Reminders", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                Text("Stay consistent and organized", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // ── 2. Summary Dashboard Card (Vibrant Orange Theme) ─────────────
            item {
                Spacer(Modifier.height(16.dp))
                ReminderSummaryCard(
                    total = totalCount,
                    todayCount = todayCount,
                    highCount = highCount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .tourTarget("reminders_summary_card")
                        .onGloballyPositioned { coords ->
                            targetRects[0] = coords.boundsInRoot()
                        }
                )
            }

            // ── 3. Quick Filter Tabs ────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned { coords ->
                            targetRects[1] = coords.boundsInRoot()
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChipItem("All Tasks", totalCount, selectedFilter == "All", OrangeAccent) { selectedFilter = "All" }
                    FilterChipItem("Due Today", todayCount, selectedFilter == "Today", Color(0xFFFF4500)) { selectedFilter = "Today" }
                    FilterChipItem("High Priority", highCount, selectedFilter == "High", RoseAccent) { selectedFilter = "High" }
                }
            }

            // ── 4. Inline Creation Form Expandable ────────────────────────────
            item {
                AnimatedVisibility(
                    visible = showInlineForm,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        InlineManageReminderForm(
                            reminderToEdit = selectedReminder,
                            viewModel = viewModel,
                            onDismiss = {
                                showInlineForm = false
                                selectedReminder = null
                            }
                        )
                    }
                }
            }

            // ── 5. Section List ─────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(OrangeAccent))
                        Text(
                            text = if (selectedFilter == "All") "Your Reminders" else "$selectedFilter Reminders",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text("${filteredReminders.size} items", fontSize = 12.sp, color = TextMuted)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (filteredReminders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🔔", fontSize = 42.sp)
                            Text("No reminders found", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Tap the + button to add your task details directly here", color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                grouped.forEach { (groupTitle, groupItems) ->
                    item {
                        val groupAccent = when (groupTitle) {
                            "Today"    -> OrangeAccent
                            "Upcoming" -> VioletAccent
                            else       -> TextMuted
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = groupTitle.uppercase(Locale.getDefault()),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = groupAccent,
                                letterSpacing = 1.sp
                            )
                            Box(modifier = Modifier.weight(1f).height(1.dp).background(GlassStroke))
                        }
                    }

                    items(groupItems, key = { it.id }) { reminder ->
                        val isFirst = groupItems.firstOrNull()?.id == reminder.id && groupTitle == grouped.firstOrNull()?.first
                        ReminderCardItem(
                            reminder = reminder,
                            isToday = groupTitle == "Today",
                            onClick = {
                                selectedReminder = reminder
                                showInlineForm = true
                            },
                            onComplete = {
                                viewModel.completeReminder(reminder.id)
                            },
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
            }

            // ── Completed Reminders Section ──────────────────────────────────
            if (completedReminders.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCompletedSection = !showCompletedSection }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (showCompletedSection) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (showCompletedSection) "Collapse" else "Expand",
                            tint = TextMuted
                        )
                        Text(
                            text = "Completed (${completedReminders.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    }
                }

                if (showCompletedSection) {
                    items(completedReminders, key = { "completed_${it.id}" }) { reminder ->
                        ReminderCardItem(
                            reminder = reminder,
                            isToday = false,
                            onClick = {
                                selectedReminder = reminder
                                showInlineForm = true
                            },
                            onComplete = {
                                viewModel.uncompleteReminder(reminder.id)
                            },
                            isCompleted = true,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // ── Floating Action Button (Orange Gradient) ───────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 90.dp)
                .size(58.dp)
                .shadow(10.dp, CircleShape, spotColor = OrangeAccent.copy(alpha = 0.4f))
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(OrangeAccent, Color(0xFFFF4500))))
                .tourTarget("reminders_fab")
                .onGloballyPositioned { coords ->
                    targetRects[3] = coords.boundsInRoot()
                }
                .clickable {
                    if (showInlineForm && selectedReminder == null) {
                        showInlineForm = false
                    } else {
                        val isPro = prefs.isPro()
                        if (!isPro && reminders.size >= 3) {
                            showProLimitSheet = true
                        } else {
                            selectedReminder = null
                            showInlineForm = true
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add, "Toggle Reminder Form",
                tint = Color.White,
                modifier = Modifier.size(26.dp).rotate(fabRotation)
            )
        }

        if (showTour) {
            // old per-screen tour disabled — global tour now used
        }
    }
}

// ─── Filter Chip Item ────────────────────────────────────────────────────────
@Composable
private fun RowScope.FilterChipItem(
    label: String,
    count: Int,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accent.copy(alpha = 0.12f) else BgCard)
            .border(1.dp, if (isSelected) accent else GlassStroke, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) accent else TextSecondary
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) accent else BgMuted)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "$count",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else TextMuted
                )
            }
        }
    }
}

// ─── Summary Dashboard Card (Orange Theme) ──────────────────────────────────
@Composable
private fun ReminderSummaryCard(
    total: Int,
    todayCount: Int,
    highCount: Int,
    modifier: Modifier
) {
    val completionRatio = if (total > 0) ((total - todayCount).coerceAtLeast(0) / total.toFloat()) else 1f
    val animRatio by animateFloatAsState(
        targetValue = completionRatio,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "summaryArc"
    )

    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(22.dp), spotColor = OrangeAccent.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(22.dp))
            .background(BgCard)
            .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Interactive Ring Canvas
            Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val sw = 9.dp.toPx()
                    val r  = size.minDimension / 2f - sw / 2f
                    drawCircle(color = BgMuted, radius = r, style = Stroke(sw))
                    drawArc(
                        brush      = Brush.sweepGradient(listOf(OrangeAccent, Color(0xFFFF4500), OrangeAccent)),
                        startAngle = -90f,
                        sweepAngle = 360f * animRatio,
                        useCenter  = false,
                        style      = Stroke(sw, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(animRatio * 100).toInt()}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OrangeAccent
                    )
                    Text("active", fontSize = 9.sp, color = TextMuted)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Schedule Overview", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    text = if (todayCount > 0) "$todayCount tasks require attention today." else "All tasks are up to date!",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBadge("📅 $todayCount TODAY", OrangeAccent)
                    StatBadge("🔴 $highCount URGENT", RoseAccent)
                }
            }
        }
    }
}

@Composable
private fun StatBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.5.dp)
    ) {
        Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

// ─── Reminder Card Item ──────────────────────────────────────────────────────
@Composable
private fun ReminderCardItem(
    reminder: ReminderEntity,
    isToday: Boolean,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    isCompleted: Boolean = false
) {
    val markerColor = runCatching {
        Color(android.graphics.Color.parseColor(reminder.markerColor))
    }.getOrDefault(OrangeAccent)

    val priorityLabel = when (reminder.priority) {
        2    -> "High Priority"
        1    -> "Medium"
        else -> "Low"
    }
    val priorityColor = when (reminder.priority) {
        2    -> RoseAccent
        1    -> Color(0xFFF59E0B)
        else -> GreenAccent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isCompleted) 0.55f else 1f)
            .shadow(3.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(
                1.dp,
                if (isToday && !isCompleted) OrangeAccent.copy(alpha = 0.5f) else GlassStroke,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left color stripe accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(CircleShape)
                .background(markerColor)
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Title + priority badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reminder.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(priorityColor.copy(alpha = 0.12f))
                        .border(1.dp, priorityColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(priorityLabel, fontSize = 10.sp, color = priorityColor, fontWeight = FontWeight.Bold)
                }
            }

            // Description
            if (!reminder.description.isNullOrBlank()) {
                Text(reminder.description, fontSize = 12.sp, color = TextMuted, maxLines = 2)
            }

            // Date/time chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                ChipTag(
                    icon = "📅",
                    text = if (reminder.startDate == reminder.endDate) reminder.startDate
                           else "${reminder.startDate} → ${reminder.endDate}",
                    highlight = isToday && !isCompleted
                )
                if (!reminder.isFullDay && !reminder.startTime.isNullOrBlank()) {
                    ChipTag(icon = "⏰", text = reminder.startTime, highlight = false)
                } else if (reminder.isFullDay) {
                    ChipTag(icon = "🌅", text = "All day", highlight = false)
                }
            }
        }

        // Quick Mark-as-Done checkmark button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isCompleted) GreenAccent.copy(alpha = 0.15f) else OrangeMuted)
                .border(1.dp, (if (isCompleted) GreenAccent else OrangeAccent).copy(alpha = 0.3f), CircleShape)
                .clickable { onComplete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Check,
                contentDescription = if (isCompleted) "Mark Active" else "Complete Task",
                tint = if (isCompleted) GreenAccent else OrangeAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ChipTag(icon: String, text: String, highlight: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlight) OrangeMuted else BgMuted)
            .border(1.dp, if (highlight) OrangeAccent.copy(alpha = 0.4f) else GlassStroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 10.sp)
        Text(
            text = text,
            fontSize = 10.5.sp,
            color = if (highlight) OrangeAccent else TextMuted,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
        )
    }
}
