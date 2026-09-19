package com.consistencygridwallpaper.ui.compose.streaks

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.storage.room.AppDatabase
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import com.consistencygridwallpaper.ui.compose.tour.tourTarget
import com.consistencygridwallpaper.ui.compose.tour.TourManager
import com.consistencygridwallpaper.ui.compose.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StreaksScreen() {
    val context = LocalContext.current
    val prefs   = remember { UserPrefs(context) }
    val db      = remember { AppDatabase.getDatabase(context) }
    val userProfile by db.userProfileDao().getFlow().collectAsState(initial = null)
    val streak      = userProfile?.currentStreak ?: prefs.getCurrentStreak()

    val dateFmt   = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today     = remember { dateFmt.format(Date()) }

    val startDate = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -111)
        dateFmt.format(cal.time)
    }

    val allLogs by db.habitLogDao().getAllLogsFrom(startDate).collectAsState(initial = emptyList())
    val activeHabits by db.habitDao().getActiveHabitsFlow().collectAsState(initial = emptyList())
    
    val habitMap = remember(activeHabits) {
        activeHabits.associateBy { it.id }
    }

    val activityMap = remember(allLogs) {
        allLogs.groupBy { it.date }.mapValues { (_, logs) -> logs.any { it.done } }
    }

    val last112Days = remember {
        (111 downTo 0).map { offset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            dateFmt.format(cal.time)
        }
    }

    val weeklyStats = remember(activityMap) {
        (6 downTo 0).map { weekOffset ->
            val weekDays = (6 downTo 0).map { dayOffset ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -(weekOffset * 7 + dayOffset))
                dateFmt.format(cal.time)
            }
            val done      = weekDays.count { activityMap[it] == true }
            val weekLabel = if (weekOffset == 0) "This wk" else "${weekOffset}w ago"
            Triple(weekLabel, done, 7)
        }.reversed()
    }

    val longestStreak = remember(activityMap) {
        var maxStreak = 0; var curStreak = 0
        last112Days.forEach { date ->
            if (activityMap[date] == true) { curStreak++; maxStreak = maxOf(maxStreak, curStreak) }
            else { curStreak = 0 }
        }
        maxStreak
    }

    val daysTracked = remember(activityMap) { activityMap.count { it.value } }

    val animStreak by animateIntAsState(
        targetValue   = streak,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label         = "streakNum"
    )

    val last30Days = remember {
        (29 downTo 0).map { offset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            dateFmt.format(cal.time)
        }
    }
    
    val completionRate30Days = remember(activityMap) {
        val completedCount = last30Days.count { activityMap[it] == true }
        if (completedCount == 0) 0f else completedCount.toFloat() / 30f
    }

    val motivationalText = remember(streak) {
        when {
            streak >= 100 -> "🏆 LEGENDARY! $streak days! You are unstoppable!"
            streak >= 30  -> "🌟 INCREDIBLE! $streak days! You're in the top 1%!"
            streak >= 14  -> "💪 TWO WEEKS STRONG! Keep the fire burning!"
            streak >= 7   -> "🔥 One full week of consistency. Real habits take hold here!"
            streak >= 3   -> "🌱 $streak days and growing! 21 days to form a habit — keep going!"
            streak == 1   -> "✨ First day done! The journey of a thousand miles begins with a single step."
            else          -> "💡 Start your streak today! Every champion was once a beginner."
        }
    }

    val milestones = listOf(
        Triple("7 days", "🌱 Bronze", 7),
        Triple("21 days", "🔥 Silver", 21),
        Triple("50 days", "⚡ Gold", 50),
        Triple("100 days", "👑 Diamond", 100)
    )

    var selectedDate by remember { mutableStateOf<String?>(null) }
    val displayDate = selectedDate ?: today

    val displayDateLogs = remember(allLogs, displayDate) {
        allLogs.filter { it.date == displayDate }
    }

    val displayDateSummaryList = remember(displayDateLogs, habitMap) {
        displayDateLogs.map { log ->
            val habitName = habitMap[log.habitId]?.title ?: "Unknown Habit"
            habitName to log.done
        }
    }

    var selectedWeekIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp)
                .background(BgCard)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier         = Modifier.size(38.dp).clip(CircleShape).background(OrangeMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Whatshot, null, tint = OrangeAccent, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Streaks", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("Your consistency journey", fontSize = 12.sp, color = TextMuted)
                }
            }
            // Today pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activityMap[today] == true) GreenMuted else BgMuted)
                    .border(1.dp, if (activityMap[today] == true) GreenAccent.copy(alpha = 0.4f) else GlassStroke, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (activityMap[today] == true) "✓ Today done" else "Today pending",
                    fontSize   = 11.sp,
                    color      = if (activityMap[today] == true) GreenAccent else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Premium Hero Ring Card ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = OrangeAccent.copy(alpha = 0.2f))
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A1C16), Color(0xFF160E0A))))
                .border(1.dp, OrangeAccent.copy(0.2f), RoundedCornerShape(24.dp))
                .tourTarget("streaks_hero_card")
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Interactive Progress Ring Widget
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 8.dp.toPx()
                        // Track
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f),
                            style = Stroke(width = strokeWidth)
                        )
                        // Progress
                        drawArc(
                            color = OrangeAccent,
                            startAngle = -90f,
                            sweepAngle = 360f * completionRate30Days,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔥", fontSize = 20.sp)
                        Text(
                            text = "$animStreak",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            lineHeight = 32.sp
                        )
                        Text(
                            text = "days",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Streak details (Longest & Tracked)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "30-Day Rate: ${(completionRate30Days * 100).toInt()}%",
                        color = OrangeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StreakStatBox("Longest", "$longestStreak days", Color.White, Color.White.copy(0.06f), Modifier.weight(1f))
                        StreakStatBox("Tracked", "$daysTracked days", Color.White, Color.White.copy(0.06f), Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Gamified Milestones Timeline ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(3.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("Consistency Milestones", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    milestones.forEachIndexed { idx, (label, name, targetVal) ->
                        val achieved = streak >= targetVal
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (achieved) OrangeAccent.copy(0.2f) else BgMuted)
                                    .border(1.5.dp, if (achieved) OrangeAccent else GlassStroke, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (achieved) {
                                    Icon(Icons.Filled.Check, null, tint = OrangeAccent, modifier = Modifier.size(16.dp))
                                } else {
                                    Text(
                                        text = "$targetVal",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (achieved) TextPrimary else TextMuted)
                            Text(label, fontSize = 8.sp, color = TextMuted)
                        }
                        
                        if (idx < milestones.size - 1) {
                            val lineFilled = streak >= milestones[idx + 1].third
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(if (lineFilled) OrangeAccent else GlassStroke)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Weekly Bar Chart (Interactive) ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(3.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment    = Alignment.CenterVertically
                ) {
                    Text("Weekly Completion", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Last 7 weeks", fontSize = 11.sp, color = TextMuted)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth().height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.Bottom
                ) {
                    weeklyStats.forEachIndexed { index, (label, done, total) ->
                        val frac     = done.toFloat() / total.toFloat()
                        val animFrac by animateFloatAsState(
                            targetValue   = frac,
                            animationSpec = tween(700, easing = FastOutSlowInEasing),
                            label         = "bar"
                        )
                        val isSelected = selectedWeekIndex == index
                        Column(
                            modifier            = Modifier
                                .weight(1f)
                                .clickable { selectedWeekIndex = if (isSelected) null else index },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (done > 0) {
                                Text("$done", fontSize = 9.sp, color = if (isSelected) Color.White else OrangeAccent, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((animFrac * 72).dp.coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .then(
                                        if (isSelected) Modifier.background(OrangeAccent)
                                        else Modifier.background(
                                            when {
                                                frac >= 0.8f -> Brush.verticalGradient(listOf(OrangeAccent, Color(0xFFFF5500)))
                                                frac >= 0.5f -> Brush.verticalGradient(listOf(OrangeAccent.copy(alpha = 0.7f), OrangeAccent.copy(alpha = 0.4f)))
                                                frac > 0f    -> Brush.verticalGradient(listOf(OrangeAccent.copy(alpha = 0.35f), OrangeAccent.copy(alpha = 0.15f)))
                                                else         -> Brush.verticalGradient(listOf(BgMuted, BgMuted))
                                            }
                                        )
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(label, fontSize = 8.sp, color = if (isSelected) OrangeAccent else TextMuted, textAlign = TextAlign.Center)
                        }
                    }
                }

                // Show Selected Week Tooltip Details
                selectedWeekIndex?.let { idx ->
                    if (idx in weeklyStats.indices) {
                        val stats = weeklyStats[idx]
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OrangeMuted)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "📅 ${stats.first}: Completed habits on ${stats.second} out of ${stats.third} days.",
                                fontSize = 12.sp,
                                color = OrangeDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 16-week Heatmap (Interactive with Labels) ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .tourTarget("streaks_heatmap")
                .padding(horizontal = 16.dp)
                .shadow(3.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment    = Alignment.CenterVertically
                ) {
                    Text("Activity Heatmap", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Less", fontSize = 9.sp, color = TextMuted)
                        listOf(0.2f, 0.45f, 0.7f, 1f).forEach { alpha ->
                            Box(
                                modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))
                                    .background(OrangeAccent.copy(alpha = alpha))
                            )
                        }
                        Text("More", fontSize = 9.sp, color = TextMuted)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Day of Week Labels
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        val dayLabels = listOf("", "Mon", "", "Wed", "", "Fri", "")
                        dayLabels.forEach { label ->
                            Box(
                                modifier = Modifier.height(15.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(label, fontSize = 8.sp, color = TextMuted)
                            }
                        }
                    }

                    // Columns
                    for (weekIndex in 0 until 16) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (dayIndex in 0 until 7) {
                                val offsetIndex = weekIndex * 7 + dayIndex
                                if (offsetIndex < last112Days.size) {
                                    val date = last112Days[offsetIndex]
                                    val isActive = activityMap[date] == true
                                    val isToday  = date == today
                                    val isSelected = selectedDate == date
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                when {
                                                    isSelected -> OrangeAccent
                                                    isToday && isActive -> OrangeAccent.copy(alpha = 0.9f)
                                                    isToday -> OrangeAccent.copy(alpha = 0.3f)
                                                    isActive -> OrangeAccent.copy(alpha = 0.7f)
                                                    else -> BgMuted
                                                }
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = when {
                                                    isSelected -> Color.White
                                                    isToday -> OrangeAccent.copy(alpha = 0.8f)
                                                    else -> Color.Transparent
                                                },
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                            .clickable {
                                                selectedDate = date
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("16 weeks ago", fontSize = 9.sp, color = TextMuted)
                    Text("Today", fontSize = 9.sp, color = if (activityMap[today] == true) OrangeAccent else TextMuted)
                }
            }
        }

        // ── Day Details Card (Interactive Heatmap Selection) ───────────────────
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(3.dp, RoundedCornerShape(16.dp), spotColor = CardShadowColor)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                val displayFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }
                val parsedDate = remember(displayDate) {
                    runCatching { dateFmt.parse(displayDate) }.getOrNull() ?: Date()
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayFormat.format(parsedDate),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activityMap[displayDate] == true) GreenMuted else OrangeMuted)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (activityMap[displayDate] == true) "Completed" else "No habits done",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activityMap[displayDate] == true) GreenAccent else OrangeDark
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                if (displayDateSummaryList.isEmpty()) {
                    Text(
                        text = "No habits were logged on this date.",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayDateSummaryList.forEach { (habitName, done) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (done) GreenMuted.copy(0.2f) else BgMuted)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = habitName,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                    contentDescription = null,
                                    tint = if (done) GreenAccent else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Motivational Footer ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(OrangeMuted)
                .border(1.dp, OrangeAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("💬", fontSize = 24.sp)
                Text(
                    text       = motivationalText,
                    color      = OrangeDark,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                    modifier   = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun StreakStatBox(label: String, value: String, textColor: Color, bgColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = textColor.copy(alpha = 0.6f), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}
