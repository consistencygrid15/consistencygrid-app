package com.consistencygridwallpaper.ui.compose.goals

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.GoalEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SubGoal(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false
)

class GoalsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val sync = SyncRepository(application)

    val rawGoals: StateFlow<List<GoalEntity>> = db.goalDao().getActiveGoalsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class GoalsDashboardStats(
        val totalGoals: Int = 0,
        val completedGoals: Int = 0,
        val inProgressGoals: Int = 0,
        val lifeMilestonesCount: Int = 0,
        val overallMomentumPercentage: Int = 0,
        val onTrackGoalsCount: Int = 0,
        val highestAgeTarget: Int = 35
    )

    val activeGoals: StateFlow<List<GoalEntity>> = rawGoals.map { raw ->
        raw.filter { !it.category.equals("Life Milestone", ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lifeMilestones: StateFlow<List<GoalEntity>> = rawGoals.map { raw ->
        raw.filter { it.category.equals("Life Milestone", ignoreCase = true) }
            // Sort by age (parsed from progress or stored separately? Wait! Android Entity doesn't have age!)
            // Currently, age isn't strictly requested by Android model... I'll sort by ID or keep original order.
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardStats: StateFlow<GoalsDashboardStats> = rawGoals.map { raw ->
        val total = raw.size
        // NextJS defines 'completed' as goal.isCompleted == true OR goal progress == 100
        val completed = raw.count { it.isCompleted || it.progress == 100 }
        
        // Active goals are those not completed
        val inProgress = total - completed
        
        val milestonesCount = raw.count { it.category.equals("Life Milestone", ignoreCase = true) }
        
        val overallPercent = if (total > 0) Math.round((completed.toFloat() / total) * 100f) else 0
        
        val onTrack = raw.count { (!it.isCompleted && it.progress < 100) && it.progress > 0 }
        
        GoalsDashboardStats(
            totalGoals = total,
            completedGoals = completed,
            inProgressGoals = inProgress,
            lifeMilestonesCount = milestonesCount,
            overallMomentumPercentage = overallPercent,
            onTrackGoalsCount = onTrack,
            highestAgeTarget = 35 // Hardcoded fallback for now, as Android model doesn't parse age yet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalsDashboardStats())

    /**
     * Parse subgoals from the JSON string stored in the GoalEntity.
     */
    fun parseSubGoals(jsonStr: String): List<SubGoal> {
        val list = mutableListOf<SubGoal>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SubGoal(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        isCompleted = obj.getBoolean("isCompleted")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GoalsVM", "Failed to parse subgoals", e)
        }
        return list
    }

    private fun serializeSubGoals(subGoals: List<SubGoal>): String {
        val arr = JSONArray()
        subGoals.forEach { sg ->
            arr.put(JSONObject().apply {
                put("id", sg.id)
                put("title", sg.title)
                put("isCompleted", sg.isCompleted)
            })
        }
        return arr.toString()
    }

    private fun calculateProgress(subGoals: List<SubGoal>): Int {
        if (subGoals.isEmpty()) return 0
        val completed = subGoals.count { it.isCompleted }
        return Math.round((completed.toFloat() / subGoals.size) * 100f)
    }

    fun addGoal(title: String, category: String, subGoals: List<SubGoal>) {
        viewModelScope.launch {
            val progress = calculateProgress(subGoals)
            val newGoal = GoalEntity(
                id = "new-${UUID.randomUUID()}",
                title = title,
                progress = progress,
                category = category,
                subGoalsJson = serializeSubGoals(subGoals),
                isCompleted = progress == 100, // Or manual? Web app keeps it till user explicitly acts or sets it. Wait, web handles it. Let's just say progress == 100.
                isSynced = false
            )
            db.goalDao().insertGoal(newGoal)
            notifyWidgets()
            triggerMetadataSync()
        }
    }

    fun updateGoal(id: String, serverId: String?, title: String, category: String, subGoals: List<SubGoal>, existingPinned: Boolean) {
        viewModelScope.launch {
            val progress = calculateProgress(subGoals)
            val entity = GoalEntity(
                id = id,
                serverId = serverId,
                title = title,
                progress = progress,
                category = category,
                isPinned = existingPinned,
                subGoalsJson = serializeSubGoals(subGoals),
                isCompleted = progress == 100,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
            db.goalDao().insertGoal(entity)
            notifyWidgets()
            triggerMetadataSync()
        }
    }
    
    fun toggleSubGoal(goal: GoalEntity, subGoalId: String) {
        viewModelScope.launch {
            val subGoals = parseSubGoals(goal.subGoalsJson).map { 
                if (it.id == subGoalId) it.copy(isCompleted = !it.isCompleted) else it 
            }
            updateGoal(goal.id, goal.serverId, goal.title, goal.category, subGoals, goal.isPinned)
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            db.goalDao().softDeleteGoal(goalId)
            notifyWidgets()
            triggerMetadataSync()
        }
    }

    private fun triggerMetadataSync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sync.syncWithServer()
                com.consistencygridwallpaper.workers.WallpaperWorker.scheduleImmediate(getApplication())
                Log.d("GoalsVM", "✅ Goals synced to server")
            } catch (e: Exception) {
                Log.w("GoalsVM", "⚠️ Sync failed — queueing retry when network available: ${e.message}")
                SyncRepository.scheduleRetry(getApplication())
            }
        }
    }

    private fun notifyWidgets() {
        com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(getApplication())
        if (!com.consistencygridwallpaper.storage.UserPrefs(getApplication()).isWidgetsOnlyMode()) {
            com.consistencygridwallpaper.workers.WallpaperWorker.scheduleImmediate(getApplication())
        }
    }
}
