package com.consistencygridwallpaper.ui.compose.wallpaper

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.ui.compose.theme.OrangeAccent
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperCanvasEngine
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperConfig
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataSource
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import org.json.JSONObject

private const val TAG = "WallpaperGeneratorScreen"

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun WallpaperGeneratorScreen(
    onBack: () -> Unit,
    onOpenSubscription: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs   = remember { UserPrefs(context) }
    val scope   = rememberCoroutineScope()

    // ── State ──────────────────────────────────────────────────────────────────
    var settings   by remember { mutableStateOf(WallpaperSettings()) }
    var bitmap     by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }
    var isSaving   by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var statusMsg  by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var activePreviewTab by remember { mutableStateOf("lockscreen") }
    var wallpaperTarget  by remember { mutableStateOf(prefs.getWallpaperTarget()) }

    LaunchedEffect(settings.wallpaperType) {
        if (settings.wallpaperType == "lockscreen" || settings.wallpaperType == "homescreen") {
            activePreviewTab = settings.wallpaperType
        }
    }

    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue    = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    // ── Helpers ────────────────────────────────────────────────────────────────
    fun showMsg(msg: String, isError: Boolean = false) {
        scope.launch { statusMsg = msg to isError; delay(3000); statusMsg = null }
    }

    /** Builds WallpaperDataState from s + Room DB, then renders via native engine. */
    fun renderBitmap(s: WallpaperSettings = settings) {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isRendering = true }
            try {
                // 1. Load Room data
                val baseState = WallpaperDataSource.getWallpaperState(context)
                // 2. Override config from current UI settings
                val config = WallpaperConfig(
                    canvasWidth         = s.width,
                    canvasHeight        = s.height,
                    theme               = s.theme,
                    dateOfBirth         = s.dob,
                    lifeExpectancyYears = s.lifeExpectancyYears,
                    yearGridMode        = s.yearGridMode,
                    wallpaperType       = s.wallpaperType,
                    showLifeGrid        = s.showLifeGrid,
                    showYearGrid        = s.showYearGrid,
                    showHabitLayer      = s.showHabitLayer,
                    showAgeStats        = s.showAgeStats,
                    showMissedDays      = s.showMissedDays,
                    showQuote           = s.showQuote,
                    quoteText           = s.quote,
                    customBackgroundUrl = s.customBackgroundUrl
                )
                val state  = baseState.copy(config = config)
                val result = WallpaperCanvasEngine.render(state)
                withContext(Dispatchers.Main) { bitmap = result; isRendering = false }
            } catch (e: Exception) {
                Log.e(TAG, "render failed: ${e.message}")
                withContext(Dispatchers.Main) { isRendering = false }
            }
        }
    }


    
    // ── Load settings on start ─────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        // 1. Local settings
        val saved = prefs.getWallpaperSettings()
        if (saved != null) {
            runCatching { settings = wallpaperSettingsFromJson(JSONObject(saved)) }
        }
        // 2. Restore custom background from file if needed
        if (settings.customBackgroundUrl.isBlank()) {
            val restored = withContext(Dispatchers.IO) { loadCustomBackgroundFromFile(context) }
            if (restored != null) {
                settings = settings.copy(customBackgroundUrl = restored)
                runCatching { prefs.saveWallpaperSettings(settings.toJson()) }
            }
        }
        // 3. Initial render
        renderBitmap(settings)
        // 4. Background sync (best-effort)
        launch(Dispatchers.IO) {
            runCatching {
                SyncRepository(context).syncWithServer()
                withContext(Dispatchers.Main) { renderBitmap(settings) }
            }
        }
        // 5. Pull server settings if none locally
        if (saved == null) {
            launch(Dispatchers.IO) {
                runCatching {
                    loadSettingsFromServer(prefs) { loaded ->
                        prefs.saveWallpaperSettings(loaded.toJson())
                        settings = loaded
                    }
                }
            }
        }
    }

    // ── Live re-render on settings change (debounced 300ms) ───────────────────
    LaunchedEffect(Unit) {
        snapshotFlow { settings }
            .debounce(300)
            .collect { s ->
                runCatching { prefs.saveWallpaperSettings(s.toJson()) }
                renderBitmap(s)
                com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(context)
            }
    }

    // ── Actions ────────────────────────────────────────────────────────────────
    fun saveSettings() {
        if (settings.dob.isBlank()) { showMsg("⚠️ Set your Date of Birth first", true); return }
        isSaving = true
        runCatching { prefs.saveWallpaperSettings(settings.toJson()) }
        com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(context)
        renderBitmap()
        scope.launch(Dispatchers.IO) {
            runCatching {
                saveSettingsToServer(prefs, settings)
                withContext(Dispatchers.Main) { showMsg("✅ Saved & synced!") }
            }.onFailure {
                withContext(Dispatchers.Main) { showMsg("✅ Saved locally") }
            }
            withContext(Dispatchers.Main) { isSaving = false }
        }
    }

    fun applyWallpaper() {
        if (isApplying) return
        isApplying = true
        scope.launch(Dispatchers.IO) {
            try {
                val target = prefs.getWallpaperTarget().trim().uppercase()
                val wm = WallpaperManager.getInstance(context)
                val baseState = WallpaperDataSource.getWallpaperState(context)

                val msg = when (target) {
                    "HOME" -> {
                        val config = WallpaperConfig(
                            canvasWidth         = settings.width,
                            canvasHeight        = settings.height,
                            theme               = settings.theme,
                            dateOfBirth         = settings.dob,
                            lifeExpectancyYears = settings.lifeExpectancyYears,
                            yearGridMode        = settings.yearGridMode,
                            wallpaperType       = "homescreen",
                            showLifeGrid        = settings.showLifeGrid,
                            showYearGrid        = settings.showYearGrid,
                            showHabitLayer      = settings.showHabitLayer,
                            showAgeStats        = settings.showAgeStats,
                            showMissedDays      = settings.showMissedDays,
                            showQuote           = settings.showQuote,
                            quoteText           = settings.quote,
                            customBackgroundUrl = settings.customBackgroundUrl
                        )
                        val bmp = WallpaperCanvasEngine.render(baseState.copy(config = config))
                        wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
                        bmp.recycle()
                        "🏠 Home screen updated!"
                    }
                    "LOCK" -> {
                        val config = WallpaperConfig(
                            canvasWidth         = settings.width,
                            canvasHeight        = settings.height,
                            theme               = settings.theme,
                            dateOfBirth         = settings.dob,
                            lifeExpectancyYears = settings.lifeExpectancyYears,
                            yearGridMode        = settings.yearGridMode,
                            wallpaperType       = "lockscreen",
                            showLifeGrid        = settings.showLifeGrid,
                            showYearGrid        = settings.showYearGrid,
                            showHabitLayer      = settings.showHabitLayer,
                            showAgeStats        = settings.showAgeStats,
                            showMissedDays      = settings.showMissedDays,
                            showQuote           = settings.showQuote,
                            quoteText           = settings.quote,
                            customBackgroundUrl = settings.customBackgroundUrl
                        )
                        val bmp = WallpaperCanvasEngine.render(baseState.copy(config = config))
                        wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
                        bmp.recycle()
                        "🔒 Lock screen updated!"
                    }
                    else -> {
                        val configHome = WallpaperConfig(
                            canvasWidth         = settings.width,
                            canvasHeight        = settings.height,
                            theme               = settings.theme,
                            dateOfBirth         = settings.dob,
                            lifeExpectancyYears = settings.lifeExpectancyYears,
                            yearGridMode        = settings.yearGridMode,
                            wallpaperType       = "homescreen",
                            showLifeGrid        = settings.showLifeGrid,
                            showYearGrid        = settings.showYearGrid,
                            showHabitLayer      = settings.showHabitLayer,
                            showAgeStats        = settings.showAgeStats,
                            showMissedDays      = settings.showMissedDays,
                            showQuote           = settings.showQuote,
                            quoteText           = settings.quote,
                            customBackgroundUrl = settings.customBackgroundUrl
                        )
                        val bmpHome = WallpaperCanvasEngine.render(baseState.copy(config = configHome))
                        wm.setBitmap(bmpHome, null, true, WallpaperManager.FLAG_SYSTEM)
                        bmpHome.recycle()

                        val configLock = WallpaperConfig(
                            canvasWidth         = settings.width,
                            canvasHeight        = settings.height,
                            theme               = settings.theme,
                            dateOfBirth         = settings.dob,
                            lifeExpectancyYears = settings.lifeExpectancyYears,
                            yearGridMode        = settings.yearGridMode,
                            wallpaperType       = "lockscreen",
                            showLifeGrid        = settings.showLifeGrid,
                            showYearGrid        = settings.showYearGrid,
                            showHabitLayer      = settings.showHabitLayer,
                            showAgeStats        = settings.showAgeStats,
                            showMissedDays      = settings.showMissedDays,
                            showQuote           = settings.showQuote,
                            quoteText           = settings.quote,
                            customBackgroundUrl = settings.customBackgroundUrl
                        )
                        val bmpLock = WallpaperCanvasEngine.render(baseState.copy(config = configLock))
                        wm.setBitmap(bmpLock, null, true, WallpaperManager.FLAG_LOCK)
                        bmpLock.recycle()
                        "✨ Both screens updated!"
                    }
                }
                withContext(Dispatchers.Main) { isApplying = false; showMsg(msg) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isApplying = false; showMsg("❌ ${e.message}", true) }
            }
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────────
    BottomSheetScaffold(
        scaffoldState       = sheetState,
        sheetPeekHeight     = 320.dp,
        sheetContainerColor = Color(0xFF121217),
        sheetShape          = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor      = Color(0xFF09090B),
        sheetContent = {
            WallpaperControlsSheet(
                settings          = settings,
                onSettingsChange  = { new ->
                    settings = new
                    runCatching { prefs.saveWallpaperSettings(new.toJson()) }
                },
                target            = wallpaperTarget,
                onTargetChange    = { newTarget ->
                    prefs.setWallpaperTarget(newTarget)
                    wallpaperTarget = newTarget
                },
                isSaving          = isSaving,
                isApplying        = isApplying,
                isRendering       = isRendering,
                onSave            = { saveSettings() },
                onApply           = { applyWallpaper() },
                onClearBackground = { clearCustomBackground(context) },
                onOpenSubscription = onOpenSubscription
            )
        },
        topBar = { GeneratorTopBar(onBack, isSaving, prefs.isPro(), { renderBitmap() }, { saveSettings() }, onOpenSubscription) }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .background(Color(0xFF09090B)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Preview Switcher
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.08f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("lockscreen" to "Lock Screen", "homescreen" to "Home Screen").forEach { (tabId, label) ->
                        val isSelected = activePreviewTab == tabId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) OrangeAccent else Color.Transparent)
                                .clickable { activePreviewTab = tabId }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.White.copy(0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Phone preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 16.dp, horizontal = 48.dp)
                        .aspectRatio(1080f / 2340f),
                    contentAlignment = Alignment.Center
                ) {
                    NativeWallpaperPreview(
                        bitmap        = bitmap,
                        isRendering   = isRendering,
                        wallpaperType = activePreviewTab,
                        modifier      = Modifier.fillMaxSize()
                    )
                }
            }

            // Status toast
            AnimatedVisibility(
                visible  = statusMsg != null,
                enter    = fadeIn() + slideInVertically { -40 },
                exit     = fadeOut() + slideOutVertically { -40 },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 336.dp)
            ) {
                val (msg, isErr) = statusMsg ?: return@AnimatedVisibility
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isErr) Color(0xFFEF4444) else Color(0xFF22C55E))
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(msg, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────────

@Composable
private fun GeneratorTopBar(
    onBack: () -> Unit,
    isSaving: Boolean,
    isPro: Boolean,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onOpenSubscription: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF09090B))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape)
                .background(Color.White.copy(0.10f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(12.dp))
        Text(
            "Wallpaper Studio",
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            color = Color.White, modifier = Modifier.weight(1f)
        )

        // Pro Button / Badge
        if (isPro) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x28FFD700))
                    .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("👑 PRO", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700))
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFFD700), Color(0xFFFF9500))
                        )
                    )
                    .clickable { onOpenSubscription() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("👑", fontSize = 11.sp)
                    Text("PRO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Refresh
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape)
                .background(Color.White.copy(0.10f))
                .clickable { onRefresh() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Refresh, "Refresh",
                tint = Color.White, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(8.dp))

        // Save pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSaving) Color.White.copy(0.1f) else OrangeAccent)
                .clickable(enabled = !isSaving) { onSave() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    color = OrangeAccent, strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text("Save", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
