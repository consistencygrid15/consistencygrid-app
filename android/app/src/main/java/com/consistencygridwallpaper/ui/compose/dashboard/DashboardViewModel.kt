package com.consistencygridwallpaper.ui.compose.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.HabitEntity
import com.consistencygridwallpaper.storage.room.HabitLogEntity
import com.consistencygridwallpaper.workers.WallpaperWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DashboardStats(
    val currentStreak: Int = 0,
    val doneToday: Int     = 0,
    val totalHabits: Int   = 0,
    val activeGoals: Int   = 0,
    val daysTracked: Int   = 0,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db   = AppDatabase.getDatabase(application)
    private val prefs = UserPrefs(application)
    private val sync  = SyncRepository(application)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Prevent auto-sync on every navigation — only sync if data is stale (>5 min old)
    private var lastSyncMs = 0L
    private val MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L  // 5 minutes

    private var wallpaperUpdateJob: kotlinx.coroutines.Job? = null

    private val _habits       = MutableStateFlow<List<HabitEntity>>(emptyList())
    val habits: StateFlow<List<HabitEntity>> = _habits.asStateFlow()

    private val _todayLogs    = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val todayLogs: StateFlow<Map<String, Boolean>> = _todayLogs.asStateFlow()

    private val _stats        = MutableStateFlow(DashboardStats())
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    private val _wallpaperUrl = MutableStateFlow<String?>(null)
    val wallpaperUrl: StateFlow<String?> = _wallpaperUrl.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    val profile: StateFlow<com.consistencygridwallpaper.storage.room.UserProfileEntity> = db.userProfileDao().getFlow()
        .map { it ?: com.consistencygridwallpaper.storage.room.UserProfileEntity() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.consistencygridwallpaper.storage.room.UserProfileEntity()
        )

    init {
        loadWallpaperUrl()
        observeHabits()
        observeTodayLogs()
        observeGoals()
        // Do NOT call refreshData() here — it fires on every navigation.
        // Background sync is triggered manually (pull-to-refresh) or on first open
        // via the gate in refreshData().
        refreshDataIfStale()
    }

    private fun loadWallpaperUrl() {
        val token = prefs.getToken()
        if (token != null) {
            val baseUrl = prefs.getBaseUrl().trimEnd('/')
            _wallpaperUrl.value = "$baseUrl/w/$token/image.png"
        }
    }

    private fun observeHabits() = viewModelScope.launch {
        db.habitDao().getActiveHabitsFlow().collect { list ->
            _habits.value = list
            updateStats()
        }
    }

    private fun observeTodayLogs() = viewModelScope.launch {
        val today = dateFmt.format(Date())
        db.habitLogDao().getLogsForDateFlow(today).collect { logs ->
            _todayLogs.value = logs.associate { it.habitId to it.done }
            updateStats()
        }
    }

    private val _goals = MutableStateFlow<List<com.consistencygridwallpaper.storage.room.GoalEntity>>(emptyList())
    val goals: StateFlow<List<com.consistencygridwallpaper.storage.room.GoalEntity>> = _goals.asStateFlow()

    private fun observeGoals() = viewModelScope.launch {
        db.goalDao().getActiveGoalsFlow().collect { goalsList ->
            _goals.value = goalsList
            _stats.update { it.copy(activeGoals = goalsList.size) }
        }
    }

    private fun updateStats() {
        val habits  = _habits.value
        val logs    = _todayLogs.value
        val done    = habits.count { logs[it.id] == true }
        _stats.update { current ->
            current.copy(
                currentStreak = prefs.getCurrentStreak(),
                doneToday     = done,
                totalHabits   = habits.size,
                daysTracked   = prefs.getDaysTracked(),
            )
        }
    }

    fun toggleHabit(habitId: String, currentStatus: Boolean) {
        val today = dateFmt.format(Date())
        viewModelScope.launch {
            val log = HabitLogEntity(
                id      = "${habitId}_${today}",
                habitId = habitId,
                date    = today,
                done    = !currentStatus,
                isSynced = false
            )
            db.habitLogDao().insertLog(log)
            // ✅ Immediately notify widgets — Room is updated, no need to wait for server
            notifyWidgets()

            if (!prefs.isWidgetsOnlyMode()) {
                wallpaperUpdateJob?.cancel()
                wallpaperUpdateJob = launch {
                    kotlinx.coroutines.delay(1500L)
                    WallpaperWorker.scheduleImmediate(getApplication())
                }
            }

            // Background push only — non-blocking, won't disrupt any running screen
            launch { sync.syncWithServer() }
        }
    }

    private fun notifyWidgets() {
        com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(getApplication())
        if (!prefs.isWidgetsOnlyMode()) {
            WallpaperWorker.scheduleImmediate(getApplication())
        }
    }

    /** Manual refresh: bypass the stale gate so pull-to-refresh always works. */
    fun refreshData() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _syncError.value = null
        viewModelScope.launch {
            try {
                val success = sync.syncWithServer()
                if (success) {
                    lastSyncMs = System.currentTimeMillis()
                } else {
                    _syncError.value = "Sync failed"
                }
            } catch (e: Exception) {
                _syncError.value = "Sync failed: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Called from init — only runs the sync if data is more than 5 min old. */
    private fun refreshDataIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastSyncMs < MIN_SYNC_INTERVAL_MS) return
        refreshData()
    }
}
