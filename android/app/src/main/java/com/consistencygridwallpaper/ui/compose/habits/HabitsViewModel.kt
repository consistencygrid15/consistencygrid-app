package com.consistencygridwallpaper.ui.compose.habits

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.consistencygridwallpaper.auth.AuthManager
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.HabitEntity
import com.consistencygridwallpaper.storage.room.HabitLogEntity
import com.consistencygridwallpaper.workers.WallpaperWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.*

class HabitsViewModel(application: Application) : AndroidViewModel(application) {
    private val db      = AppDatabase.getDatabase(application)
    private val sync    = SyncRepository(application)
    private val prefs   = UserPrefs(application)
    private val auth    = AuthManager.getInstance(application)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var lastSyncMs = 0L
    private val MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L

    // Debounce: wait 1.5s after last toggle before firing wallpaper update
    private var wallpaperUpdateJob: Job? = null

    val habits: StateFlow<List<HabitEntity>> = db.habitDao()
        .getActiveHabitsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _todayLogs = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val todayLogs: StateFlow<Map<String, Boolean>> = _todayLogs.asStateFlow()

    // habitId → list of 30 booleans (day 0 = 30 days ago, day 29 = today)
    private val _heatmapData = MutableStateFlow<Map<String, List<Boolean>>>(emptyMap())
    val heatmapData: StateFlow<Map<String, List<Boolean>>> = _heatmapData.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // null = no error, non-null = error message to show in a snackbar
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        observeTodayLogs()
        observeHeatmap()
        refreshIfStale()  // only syncs if >5 min stale, not on every navigation
    }

    private fun observeTodayLogs() = viewModelScope.launch {
        val today = dateFmt.format(Date())
        db.habitLogDao().getLogsForDateFlow(today).collect { logs ->
            _todayLogs.value = logs.associate { it.habitId to it.done }
        }
    }

    private fun observeHeatmap() = viewModelScope.launch {
        val thirtyDaysAgo = run {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -29)
            dateFmt.format(cal.time)
        }
        db.habitLogDao().getAllLogsFrom(thirtyDaysAgo).collect { logs ->
            val byHabit = logs.groupBy { it.habitId }
            val last30 = (29 downTo 0).map { offset ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -offset)
                dateFmt.format(cal.time)
            }
            val map = byHabit.mapValues { (_, habitLogs) ->
                val logByDate = habitLogs.associate { it.date to it.done }
                last30.map { date -> logByDate[date] == true }
            }
            _heatmapData.value = map
        }
    }

    fun toggleHabit(habitId: String, isDone: Boolean) = viewModelScope.launch {
        val today = dateFmt.format(Date())

        // ── Step 1: Upsert to local Room DB with STABLE ID ─────────────────────
        val newDone = !isDone
        db.habitLogDao().insertLog(
            HabitLogEntity(
                id       = "${habitId}_${today}",   // deterministic — matches server format
                habitId  = habitId,
                date     = today,
                done     = newDone,
                isSynced = false
            )
        )
        Log.d("HabitsVM", "✅ Habit toggled locally: $habitId → done=$newDone")

        // ── Step 2: Notify widgets immediately (Room is already updated) ───────
        notifyWidgets()

        // ── Step 3: Debounced wallpaper re-render ───────────────────────────────
        // Wait 1.5s after last toggle — rapid taps batch into one render.
        // Skip entirely if the user is in Widgets-Only mode; widget notification
        // above (Step 2) is sufficient in that case.
        if (!prefs.isWidgetsOnlyMode()) {
            wallpaperUpdateJob?.cancel()
            wallpaperUpdateJob = launch {
                delay(1500L)
                WallpaperWorker.scheduleImmediate(getApplication())
                Log.d("HabitsVM", "📲 Wallpaper update scheduled natively (done=$newDone)")
            }
        }

        // ── Step 3: Sync to server quietly in background ───────────────────────
        launch {
            try {
                sync.syncWithServer()
            } catch (e: Exception) {
                Log.w("HabitsVM", "⚠️ Sync failed (offline?) — queueing retry: ${e.message}")
                SyncRepository.scheduleRetry(getApplication())
            }
        }
    }


    fun addHabit(title: String, time: String?) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        _isSaving.value = true
        _saveError.value = null
        try {
            // ── Offline-first: insert into Room immediately ──────────────────────
            // The habit will appear in the UI right away, even without internet.
            // SyncRepository will push it to the server on the next sync.
            val newHabit = com.consistencygridwallpaper.storage.room.HabitEntity(
                id            = "local-${java.util.UUID.randomUUID()}",  // temporary local ID
                serverId      = null,          // not yet on server
                title         = title.trim(),
                scheduledTime = time?.takeIf { it.isNotBlank() },
                isActive      = true,
                isSynced      = false          // marks it as pending push
            )
            db.habitDao().insertHabits(listOf(newHabit))
            Log.d("HabitsVM", "✅ Habit inserted locally: ${newHabit.id}")

            // Notify widgets of the local change immediately
            notifyWidgets()

            // ── Background push to server ────────────────────────────────────────
            launch(Dispatchers.IO) {
                try {
                    sync.syncWithServer()
                    Log.d("HabitsVM", "✅ Habit synced to server")
                } catch (e: Exception) {
                    Log.w("HabitsVM", "⚠️ Sync failed — queueing retry: ${e.message}")
                    SyncRepository.scheduleRetry(getApplication())
                }
            }
        } catch (e: Exception) {
            _saveError.value = "Failed to save habit. Please try again."
            Log.e("HabitsVM", "addHabit exception", e)
        } finally {
            _isSaving.value = false
        }
    }

    /** Call this from the UI after showing the snackbar to dismiss it. */
    fun clearSaveError() { _saveError.value = null }

    fun deleteHabit(habitId: String) = viewModelScope.launch {
        try {
            // Soft-delete locally so sync can inform the server
            db.habitDao().softDeleteHabit(habitId)
            Log.d("HabitsVM", "🗑️ Habit soft-deleted locally: $habitId")
            notifyWidgets()
            // Push delete to server in background
            launch(Dispatchers.IO) {
                try { sync.syncWithServer() }
                catch (e: Exception) { Log.w("HabitsVM", "Delete sync failed (will retry): ${e.message}") }
            }
        } catch (e: Exception) {
            Log.e("HabitsVM", "deleteHabit exception", e)
        }
    }

    fun updateHabitTitle(habitId: String, newTitle: String, newTime: String?) = viewModelScope.launch {
        if (newTitle.isBlank()) return@launch
        try {
            val existing = db.habitDao().getHabitById(habitId) ?: return@launch
            db.habitDao().insertHabits(listOf(
                existing.copy(
                    title         = newTitle.trim(),
                    scheduledTime = newTime?.takeIf { it.isNotBlank() },
                    updatedAt     = System.currentTimeMillis(),
                    isSynced      = false
                )
            ))
            Log.d("HabitsVM", "✏️ Habit updated locally: $habitId")
            notifyWidgets()
            launch(Dispatchers.IO) {
                try { sync.syncWithServer() }
                catch (e: Exception) { Log.w("HabitsVM", "Update sync failed: ${e.message}") }
            }
        } catch (e: Exception) {
            Log.e("HabitsVM", "updateHabitTitle exception", e)
        }
    }

    private fun notifyWidgets() {
        com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(getApplication())
        if (!prefs.isWidgetsOnlyMode()) {
            WallpaperWorker.scheduleImmediate(getApplication())
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            sync.syncWithServer()
            lastSyncMs = System.currentTimeMillis()
            _isRefreshing.value = false
        }
    }

    private fun refreshIfStale() {
        if (System.currentTimeMillis() - lastSyncMs < MIN_SYNC_INTERVAL_MS) return
        refresh()
    }
}
