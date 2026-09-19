package com.consistencygridwallpaper.ui.compose.wallpaper

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.ui.compose.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

// ─────────────────────────────────────────────────────────────────────────────
// Layout Definitions
// ─────────────────────────────────────────────────────────────────────────────
data class LayoutOption(val id: String, val icon: ImageVector, val name: String)
val LAYOUT_OPTIONS = listOf(
    LayoutOption("weeks",      Icons.Rounded.ViewWeek, "Weeks"),
    LayoutOption("days",       Icons.Rounded.CalendarMonth, "Days"),
    LayoutOption("life",       Icons.Rounded.HourglassBottom, "Life"),
    LayoutOption("week_strip", Icons.Rounded.ViewDay, "Strip"),
    LayoutOption("month",      Icons.Rounded.CalendarToday, "Month")
)

data class WallpaperTypeOption(val id: String, val icon: ImageVector, val name: String)
val WALLPAPER_TYPES = listOf(
    WallpaperTypeOption("lockscreen", Icons.Rounded.Lock, "Lock Screen"),
    WallpaperTypeOption("homescreen", Icons.Rounded.Home, "Home Screen"),
    WallpaperTypeOption("calendar",   Icons.Rounded.Event, "Calendar")
)

// ─────────────────────────────────────────────────────────────────────────────
// Main Sheet Content
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun WallpaperControlsSheet(
    settings: WallpaperSettings,
    onSettingsChange: (WallpaperSettings) -> Unit,
    target: String,
    onTargetChange: (String) -> Unit,
    isSaving: Boolean,
    isApplying: Boolean,
    isRendering: Boolean,
    onSave: () -> Unit,
    onApply: () -> Unit,
    onClearBackground: () -> Unit,
    onOpenSubscription: () -> Unit = {}
) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val userPrefs   = remember { com.consistencygridwallpaper.storage.UserPrefs(context) }
    val isProUser   = userPrefs.isPro()

    var proSheetTitle     by remember { mutableStateOf("") }
    var proSheetDesc      by remember { mutableStateOf("") }
    var proSheetHighlight by remember { mutableStateOf("") }
    var showProSheet      by remember { mutableStateOf(false) }

    fun promptPro(title: String, desc: String, highlight: String) {
        proSheetTitle     = title
        proSheetDesc      = desc
        proSheetHighlight = highlight
        showProSheet      = true
    }

    if (showProSheet) {
        com.consistencygridwallpaper.ui.compose.habits.ProFeatureLimitSheet(
            title            = proSheetTitle,
            description      = proSheetDesc,
            featureHighlight = proSheetHighlight,
            onUpgrade        = {
                showProSheet = false
                onOpenSubscription()
            },
            onDismiss        = { showProSheet = false }
        )
    }

    var isPickingImage by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        isPickingImage = false
        if (uri != null) {
            scope.launch {
                val dataUrl = uriToBase64DataUrl(context, uri)
                if (dataUrl != null) {
                    val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(context)
                    userPrefs.setWallpaperUpdateMode(com.consistencygridwallpaper.storage.UserPrefs.UPDATE_MODE_WALLPAPER_AND_WIDGETS)
                    onSettingsChange(settings.copy(customBackgroundUrl = dataUrl))
                    com.consistencygridwallpaper.workers.WallpaperWorker.scheduleImmediate(context)
                }
            }
        }
    }

    var activeTab by remember { mutableStateOf("layout") } // "layout", "style", "profile", "display"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 60.dp)
    ) {
        // Drag handle
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF3F3F4E)))
        }

        // ── 1. HERO APPLY BUTTON ─────────────────────────────────────────────────
        HeroApplyButton(isApplying, isRendering, onApply)

        Spacer(Modifier.height(20.dp))

        // ── TABS SELECTOR ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E26))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tabsList = listOf(
                "layout" to "Layout",
                "style" to "Style",
                "profile" to "Profile",
                "display" to "Display"
            )
            tabsList.forEach { (tabId, label) ->
                val isSelected = activeTab == tabId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) OrangeAccent.copy(0.18f) else Color.Transparent)
                        .clickable { activeTab = tabId }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) OrangeAccent else Color.White.copy(0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── TAB CONTENT ─────────────────────────────────────────────────────────
        when (activeTab) {
            "layout" -> {
                // ── LAYOUT PRESETS (Horizontal Chips) ─────────────────────────────
                SheetSectionHeader(Icons.Rounded.GridView, "Grid Layout")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(LAYOUT_OPTIONS) { layout ->
                        val sel = settings.yearGridMode == layout.id
                        val isProLayout = layout.id == "life"
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) OrangeAccent.copy(0.15f) else Color(0xFF1E1E26))
                                .border(1.5.dp, if (sel) OrangeAccent else Color(0xFF2E2E38), RoundedCornerShape(16.dp))
                                .clickable {
                                    if (isProLayout && !isProUser) {
                                        promptPro(
                                            "Life in Weeks (Pro)",
                                            "Visualize your entire lifespan week by week. Upgrade to Pro to unlock this layout mode.",
                                            "Pro unlocks Life in Weeks and all advanced wallpaper layouts."
                                        )
                                    } else {
                                        onSettingsChange(settings.copy(yearGridMode = layout.id))
                                    }
                                }
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(layout.icon, contentDescription = null, tint = if (sel) OrangeAccent else Color.White.copy(0.7f), modifier = Modifier.size(24.dp))
                                if (isProLayout && !isProUser) {
                                    Text("👑", fontSize = 10.sp, modifier = Modifier.offset(x = 10.dp, y = (-4).dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(layout.name, color = if (sel) OrangeAccent else Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── WALLPAPER DESTINATION SELECTOR ──────────────────
                SheetSectionHeader(Icons.Rounded.SettingsSuggest, "Wallpaper Destination")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E1E26))
                        .border(1.dp, Color(0xFF2E2E38), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val targetOptions = listOf(
                        "BOTH" to "Both Screens",
                        "HOME" to "Home Only",
                        "LOCK" to "Lock Only",
                        "NONE" to "Disable Sync"
                    )
                    targetOptions.forEach { (value, label) ->
                        val isSelected = target == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) OrangeAccent.copy(0.18f) else Color.Transparent)
                                .clickable { onTargetChange(value) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) OrangeAccent else Color.White.copy(0.6f),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── WALLPAPER TYPE ────────────────────────────────────────────────
                SheetSectionHeader(Icons.Rounded.PhonelinkSetup, "Preview Mode")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1E1E26)).border(1.dp, Color(0xFF2E2E38), RoundedCornerShape(14.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WALLPAPER_TYPES.forEach { wt ->
                        val sel = settings.wallpaperType == wt.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) OrangeAccent.copy(0.18f) else Color.Transparent)
                                .clickable { onSettingsChange(settings.copy(wallpaperType = wt.id)) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(wt.icon, contentDescription = null, tint = if (sel) OrangeAccent else Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
                                Text(wt.name, color = if (sel) OrangeAccent else Color.White.copy(0.6f), fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            "style" -> {
                // ── THEME PALETTE ──────────────────────────────────────────────────
                SheetSectionHeader(Icons.Rounded.Palette, "Color Theme")
                ThemeGrid(
                    selectedThemeId = settings.theme,
                    isProUser       = isProUser,
                    onThemeSelected = { onSettingsChange(settings.copy(theme = it)) },
                    onRequirePro    = {
                        promptPro(
                            "Premium Color Theme (Pro)",
                            "Personalize your wallpaper with beautiful curated aesthetic themes. Upgrade to Pro to unlock all color palettes.",
                            "Pro unlocks all premium theme palettes."
                        )
                    }
                )

                Spacer(Modifier.height(24.dp))

                // ── BACKGROUND PHOTO ──────────────────────────────────────────────
                SheetSectionHeader(Icons.Rounded.CameraAlt, "Background Photo")
                SheetCard(Modifier.padding(horizontal = 16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (settings.customBackgroundUrl.isNotBlank()) {
                            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))) {
                                val bitmap = remember(settings.customBackgroundUrl) {
                                    try {
                                        val clean = if (settings.customBackgroundUrl.contains(",")) settings.customBackgroundUrl.split(",")[1] else settings.customBackgroundUrl
                                        val bytes = Base64.decode(clean, Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    } catch (_: Exception) { null }
                                }
                                if (bitmap != null) {
                                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                                Box(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEF4444).copy(0.9f)).clickable {
                                        onClearBackground()
                                        onSettingsChange(settings.copy(customBackgroundUrl = ""))
                                    }.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Remove", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2E2E38)).clickable(enabled = !isPickingImage) {
                                    if (!isProUser) {
                                        promptPro(
                                            "Custom Photo Background (Pro)",
                                            "Set your favorite photos or artwork behind your consistency grid. Upgrade to Pro to use custom wallpapers.",
                                            "Pro lets you use any photo as your live wallpaper background."
                                        )
                                    } else {
                                        isPickingImage = true
                                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPickingImage) {
                                CircularProgressIndicator(color = OrangeAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text(if (settings.customBackgroundUrl.isNotBlank()) "Change Photo" else "Upload Photo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    if (!isProUser) {
                                        Text("👑", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── CUSTOM QUOTE TEXT FIELD ───────────────────────────────────────
                if (settings.showQuote) {
                    Spacer(Modifier.height(24.dp))
                    SheetSectionHeader(Icons.Rounded.FormatQuote, "Quote Text")
                    SheetCard(Modifier.padding(horizontal = 16.dp)) {
                        OutlinedTextField(
                            value = settings.quote,
                            onValueChange = { if (it.length <= 100) onSettingsChange(settings.copy(quote = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangeAccent, unfocusedBorderColor = Color(0xFF2E2E38),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1E1E26), unfocusedContainerColor = Color(0xFF1E1E26)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }
            }
            "profile" -> {
                // ── BASIC INFO ────────────────────────────────────────────────────
                SheetSectionHeader(Icons.Rounded.PersonOutline, "Profile & Goals")
                SheetCard(Modifier.padding(horizontal = 16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // DOB
                        Text("DATE OF BIRTH", color = Color.White.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E1E26))
                                .border(1.dp, if (settings.dob.isNotBlank()) OrangeAccent.copy(0.5f) else Color(0xFF2E2E38), RoundedCornerShape(12.dp))
                                .clickable {
                                    val p = settings.dob.split("-")
                                    DatePickerDialog(context, { _, y, m, d ->
                                        onSettingsChange(settings.copy(dob = "$y-${(m+1).toString().padStart(2,'0')}-${d.toString().padStart(2,'0')}"))
                                    }, p.getOrNull(0)?.toIntOrNull() ?: 2000, (p.getOrNull(1)?.toIntOrNull() ?: 1)-1, p.getOrNull(2)?.toIntOrNull() ?: 1).show()
                                }.padding(14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (settings.dob.isNotBlank()) settings.dob else "Select date of birth", color = if (settings.dob.isNotBlank()) Color.White else Color.White.copy(0.6f), fontSize = 14.sp)
                                Icon(Icons.Filled.CalendarMonth, null, tint = if (settings.dob.isNotBlank()) OrangeAccent else Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
                            }
                        }

                        HorizontalDivider(color = Color(0xFF2E2E38))

                        // Life Expectancy
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("LIFE EXPECTANCY", color = Color.White.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(OrangeAccent.copy(0.15f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text("${settings.lifeExpectancyYears} yrs", color = OrangeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Slider(
                            value = settings.lifeExpectancyYears.toFloat(),
                            onValueChange = { onSettingsChange(settings.copy(lifeExpectancyYears = it.toInt())) },
                            valueRange = 50f..100f,
                            colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = OrangeAccent, inactiveTrackColor = Color(0xFF2E2E38))
                        )
                    }
                }
            }
            "display" -> {
                // ── DISPLAY TOGGLES ───────────────────────────────────────────────
                SheetSectionHeader(Icons.Rounded.Visibility, "Display Elements")
                SheetCard(Modifier.padding(horizontal = 16.dp)) {
                    Column {
                        val toggles = listOf(
                            Triple("showAgeStats",   settings.showAgeStats,   "Progress Ring"    to "Show life % and streak"),
                            Triple("showHabitLayer", settings.showHabitLayer, "Habit Layer"      to "Show habits section"),
                            Triple("goalEnabled",    settings.goalEnabled,    "Goal Tracking"    to "Show active goal progress"),
                            Triple("showQuote",      settings.showQuote,      "Custom Quote"     to "Show quote at bottom")
                        )
                        toggles.forEachIndexed { idx, (key, value, labels) ->
                            if (idx > 0) HorizontalDivider(color = Color(0xFF2E2E38))
                            ToggleSettingRow(labels.first, labels.second, value) { checked ->
                                onSettingsChange(when (key) {
                                    "showAgeStats"   -> settings.copy(showAgeStats   = checked)
                                    "showHabitLayer" -> settings.copy(showHabitLayer = checked)
                                    "goalEnabled"    -> settings.copy(goalEnabled    = checked)
                                    "showQuote"      -> settings.copy(showQuote      = checked)
                                    else             -> settings
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroApplyButton(isApplying: Boolean, isRendering: Boolean, onApply: () -> Unit) {
    val enabled = !isApplying && !isRendering

    val scale by animateFloatAsState(if (isApplying) 0.98f else 1f, label = "scale")
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .scale(scale)
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(OrangeAccent, Color(0xFFFF5500)))
                else SolidColor(Color(0xFF2C2C36))
            )
            .clickable(enabled = enabled) { onApply() },
        contentAlignment = Alignment.Center
    ) {
        if (isApplying) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                Text("Applying Wallpaper…", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Wallpaper, null, tint = if (enabled) Color.Black else Color.White.copy(0.4f), modifier = Modifier.size(20.dp))
                Text("Set Wallpaper", color = if (enabled) Color.Black else Color.White.copy(0.4f), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ThemeGrid(
    selectedThemeId: String,
    isProUser: Boolean,
    onThemeSelected: (String) -> Unit,
    onRequirePro: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val rows = THEMES.chunked(2)
        rows.forEach { rowThemes ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowThemes.forEach { theme ->
                    val sel = theme.id == selectedThemeId
                    val isProTheme = theme.id !in listOf("minimal-dark", "sunset-orange")
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) OrangeAccent.copy(0.18f) else Color(0xFF1E1E26))
                            .border(1.5.dp, if (sel) OrangeAccent else Color(0xFF2E2E38), RoundedCornerShape(14.dp))
                            .clickable {
                                if (isProTheme && !isProUser) {
                                    onRequirePro()
                                } else {
                                    onThemeSelected(theme.id)
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gradient color swatch showing actual theme colors
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        theme.previewColors.map { Color(it) }
                                            .let { if (it.size < 2) it + it else it }
                                    )
                                )
                                .border(1.5.dp, if (sel) OrangeAccent else Color(0xFF2E2E38), CircleShape)
                        ) {
                            if (sel) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(theme.name, color = if (sel) OrangeAccent else Color.White.copy(0.8f), fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                        if (isProTheme && !isProUser) {
                            Spacer(Modifier.weight(1f))
                            Text("👑", fontSize = 11.sp)
                        }
                    }
                }
                // Handle odd number of themes
                if (rowThemes.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SheetSectionHeader(icon: ImageVector, title: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(22.dp))
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SheetCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ToggleSettingRow(label: String, sublabel: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sublabel, color = Color.White.copy(0.6f), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheck,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OrangeAccent,
                uncheckedTrackColor = Color(0xFF2E2E38),
                uncheckedThumbColor = Color.LightGray
            )
        )
    }
}
