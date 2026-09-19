package com.consistencygridwallpaper.ui.compose.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.ui.compose.theme.*
import com.consistencygridwallpaper.widget.*

// ── Data model ────────────────────────────────────────────────────────────────

private data class WidgetInfo(
    val name: String,
    val description: String,
    val accentColor: Color,
    val dimColor: Color,
    val icon: ImageVector,
    val tag: String, // category tag
    val providerClass: Class<*>,
    val preview: @Composable () -> Unit
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun WidgetsGalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pinnedWidgetName by remember { mutableStateOf<String?>(null) }

    val widgets = remember { buildWidgetList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        // ── Top Bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp)
                .background(BgCard)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(OrangeMuted)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OrangeAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Widget Gallery",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    "Add widgets to your home screen",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            // Widget count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(OrangeDim)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${widgets.size} widgets",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrangeAccent
                )
            }
        }

        // ── Intro callout ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(OrangeDim)
                .border(1.dp, OrangeAccent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.Widgets,
                    contentDescription = null,
                    tint = OrangeAccent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Tap \"Add to Home Screen\" on any widget to pin it directly. On older devices, long-press your home screen \u2192 Widgets.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        // ── Widget Grid ──────────────────────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(widgets) { widget ->
                WidgetCard(
                    widget = widget,
                    onPin = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val awm = AppWidgetManager.getInstance(context)
                            val provider = ComponentName(context, widget.providerClass)
                            if (awm.isRequestPinAppWidgetSupported) {
                                awm.requestPinAppWidget(provider, null, null)
                                pinnedWidgetName = widget.name
                            } else {
                                Toast.makeText(
                                    context,
                                    "Long-press home screen → Widgets → ${widget.name}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Long-press home screen → Widgets → ${widget.name}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
            // Bottom padding item
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // ── Success snackbar ──────────────────────────────────────────────────────
    pinnedWidgetName?.let { name ->
        LaunchedEffect(name) {
            kotlinx.coroutines.delay(3000)
            pinnedWidgetName = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(GreenAccent)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "✅ Adding \"$name\" to home screen…",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Widget Card ───────────────────────────────────────────────────────────────

@Composable
private fun WidgetCard(
    widget: WidgetInfo,
    onPin: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .shadow(3.dp, RoundedCornerShape(18.dp), spotColor = widget.accentColor.copy(0.15f))
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .border(1.dp, widget.accentColor.copy(0.15f), RoundedCornerShape(18.dp))
    ) {
        // ── Mock Preview Area ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.8f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color(0xFF1C1C1E))
        ) {
            widget.preview()
        }

        // ── Info + Button ──────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tag chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(widget.dimColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    widget.tag.uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = widget.accentColor,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                widget.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                widget.description,
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            // Add button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(widget.accentColor, widget.accentColor.copy(0.8f))
                        )
                    )
                    .clickable { onPin() }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        "Add to Home Screen",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ── Mock Preview Composables ──────────────────────────────────────────────────

@Composable
private fun GridWidgetMockPreview(accentColor: Color, rows: Int = 5, cols: Int = 12) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Header stub
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor)
        )
        Spacer(Modifier.height(2.dp))
        // Grid cells
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(cols) { col ->
                    val filled = (row * cols + col) < (rows * cols * 0.65).toInt()
                    Box(
                        modifier = Modifier
                            .size(width = 8.dp, height = 6.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (filled) accentColor.copy(alpha = 0.85f)
                                else Color.White.copy(alpha = 0.1f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsWidgetMockPreview(accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Streak circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(2.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("21", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = accentColor)
                Text("days", fontSize = 7.sp, color = Color.White.copy(0.6f))
            }
        }
        // Bars
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { i ->
                val w = listOf(0.9f, 0.7f, 0.55f)[i]
                Box(
                    modifier = Modifier
                        .fillMaxWidth(w)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accentColor.copy(alpha = 0.7f - i * 0.15f))
                )
            }
        }
    }
}

@Composable
private fun ListWidgetMockPreview(accentColor: Color, rows: Int = 3) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accentColor)
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accentColor.copy(0.3f))
        )
        Spacer(Modifier.height(2.dp))
        // Rows
        repeat(rows) { i ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(accentColor)
                )
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White.copy(0.5f), CircleShape)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        modifier = Modifier
                            .width((50 + i * 15).dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(0.85f))
                    )
                    Box(
                        modifier = Modifier
                            .width((30 + i * 8).dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(0.3f))
                    )
                }
            }
        }
    }
}

// ── Widget List Builder ───────────────────────────────────────────────────────

private fun buildWidgetList(): List<WidgetInfo> = listOf(
    WidgetInfo(
        name = "Year Grid",
        description = "See your full year of habit consistency in one glance.",
        accentColor = Color(0xFFFF7A00),
        dimColor = Color(0x28FF7A00),
        icon = Icons.Filled.GridOn,
        tag = "Wallpaper",
        providerClass = YearGridWidgetProvider::class.java,
        preview = { GridWidgetMockPreview(accentColor = Color(0xFFFF7A00), rows = 5, cols = 12) }
    ),
    WidgetInfo(
        name = "Month Grid",
        description = "Track your monthly consistency in a calendar grid.",
        accentColor = Color(0xFFFF7A00),
        dimColor = Color(0x28FF7A00),
        icon = Icons.Filled.CalendarMonth,
        tag = "Wallpaper",
        providerClass = MonthGridWidgetProvider::class.java,
        preview = { GridWidgetMockPreview(accentColor = Color(0xFFFF7A00), rows = 4, cols = 7) }
    ),
    WidgetInfo(
        name = "Life Grid",
        description = "Visualize your life in weeks — Memento Mori.",
        accentColor = Color(0xFFFF7A00),
        dimColor = Color(0x28FF7A00),
        icon = Icons.Filled.Favorite,
        tag = "Wallpaper",
        providerClass = LifeGridWidgetProvider::class.java,
        preview = { GridWidgetMockPreview(accentColor = Color(0xFFFF7A00), rows = 6, cols = 10) }
    ),
    WidgetInfo(
        name = "Stats",
        description = "Current streak, completion rate and weekly bars.",
        accentColor = Color(0xFFFF7A00),
        dimColor = Color(0x28FF7A00),
        icon = Icons.Filled.BarChart,
        tag = "Analytics",
        providerClass = StatsWidgetProvider::class.java,
        preview = { StatsWidgetMockPreview(accentColor = Color(0xFFFF7A00)) }
    ),
    WidgetInfo(
        name = "Week Agenda",
        description = "Your 7-day habit performance at a glance.",
        accentColor = Color(0xFFFF7A00),
        dimColor = Color(0x28FF7A00),
        icon = Icons.Filled.DateRange,
        tag = "Agenda",
        providerClass = WeekAgendaWidgetProvider::class.java,
        preview = { GridWidgetMockPreview(accentColor = Color(0xFFFF7A00), rows = 2, cols = 7) }
    ),
    WidgetInfo(
        name = "Habit Tracker",
        description = "Tick off daily habits without opening the app.",
        accentColor = Color(0xFFFF7A00),
        dimColor = Color(0x28FF7A00),
        icon = Icons.Filled.CheckCircle,
        tag = "Interactive",
        providerClass = HabitWidgetProvider::class.java,
        preview = { ListWidgetMockPreview(accentColor = Color(0xFFFF7A00)) }
    ),
    WidgetInfo(
        name = "Goal Tracker",
        description = "Mark goals complete directly from home screen.",
        accentColor = Color(0xFF6B46C1),
        dimColor = Color(0x286B46C1),
        icon = Icons.Filled.Flag,
        tag = "Interactive",
        providerClass = GoalWidgetProvider::class.java,
        preview = { ListWidgetMockPreview(accentColor = Color(0xFF6B46C1)) }
    ),
    WidgetInfo(
        name = "Reminder Alerts",
        description = "Dismiss pending reminders from your home screen.",
        accentColor = Color(0xFF0284C7),
        dimColor = Color(0x280284C7),
        icon = Icons.Filled.Notifications,
        tag = "Interactive",
        providerClass = ReminderWidgetProvider::class.java,
        preview = { ListWidgetMockPreview(accentColor = Color(0xFF0284C7)) }
    )
)
