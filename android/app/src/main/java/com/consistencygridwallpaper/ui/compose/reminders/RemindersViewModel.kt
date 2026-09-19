package com.consistencygridwallpaper.ui.compose.reminders

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.ReminderEntity
import com.consistencygridwallpaper.workers.WallpaperWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class RemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val db          = AppDatabase.getDatabase(application)
    private val reminderDao = db.reminderDao()
    private val sync        = SyncRepository(application)

    val uncompletedReminders: StateFlow<List<ReminderEntity>> = reminderDao.getRemindersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val completedReminders: StateFlow<List<ReminderEntity>> = reminderDao.getCompletedRemindersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addReminder(
        title: String,
        description: String?,
        startDate: String,
        endDate: String,
        startTime: String?,
        endTime: String?,
        isFullDay: Boolean,
        priority: Int,
        markerColor: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val newReminder = ReminderEntity(
                id = UUID.randomUUID().toString(),
                serverId = null,
                title = title,
                description = description,
                startDate = startDate,
                endDate = endDate,
                startTime = startTime,
                endTime = endTime,
                isFullDay = isFullDay,
                priority = priority,
                markerColor = markerColor,
                isSynced = false,
                isDeleted = false,
                updatedAt = System.currentTimeMillis()
            )
            reminderDao.insertReminder(newReminder)
            triggerMetadataSync()
        }
    }

    fun updateReminder(
        id: String,
        serverId: String?,
        title: String,
        description: String?,
        startDate: String,
        endDate: String,
        startTime: String?,
        endTime: String?,
        isFullDay: Boolean,
        priority: Int,
        markerColor: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedReminder = ReminderEntity(
                id = id,
                serverId = serverId,
                title = title,
                description = description,
                startDate = startDate,
                endDate = endDate,
                startTime = startTime,
                endTime = endTime,
                isFullDay = isFullDay,
                priority = priority,
                markerColor = markerColor,
                isSynced = false,
                isDeleted = false,
                updatedAt = System.currentTimeMillis()
            )
            reminderDao.insertReminder(updatedReminder)
            triggerMetadataSync()
        }
    }

    fun completeReminder(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderDao.completeReminder(id)
            triggerMetadataSync()
        }
    }

    fun uncompleteReminder(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderDao.uncompleteReminder(id)
            triggerMetadataSync()
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderDao.softDeleteReminder(id)
            triggerMetadataSync()
        }
    }

    private fun triggerMetadataSync() {
        Log.d("RemindersVM", "Triggering sync due to local mutation")
        // 1. Update wallpaper to reflect the change
        WallpaperWorker.scheduleImmediate(getApplication())
        // 2. Notify widgets
        notifyWidgets()
        // 3. Push the new/updated/deleted reminder to the server
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sync.syncWithServer()
                Log.d("RemindersVM", "✅ Reminder sync completed")
            } catch (e: Exception) {
                Log.w("RemindersVM", "⚠️ Reminder sync failed (offline?): ${e.message}")
                // Data is saved locally with isSynced=false — will retry on next sync
            }
        }
    }

    private fun notifyWidgets() {
        com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(getApplication())
    }
}
