package com.consistencygridwallpaper.repository

import android.content.Context
import android.util.Log
import com.consistencygridwallpaper.network.ApiClient
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.storage.room.*
import com.consistencygridwallpaper.reelcontrol.data.PreferencesManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy

class SyncRepository(private val context: Context) {
    private val db                 = AppDatabase.getDatabase(context)
    private val prefs              = UserPrefs(context)
    private val reelControllerRepo = ReelControllerRepository(context)

    companion object {
        private const val TAG = "SyncRepository"
        // Global lock — prevents thundering-herd when internet reconnects and every
        // ViewModel fires syncWithServer() at the same millisecond.
        private val isSyncing = java.util.concurrent.atomic.AtomicBoolean(false)

        fun scheduleRetry(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<com.consistencygridwallpaper.workers.HabitSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "habit_one_time_sync",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private fun api() = ApiClient.getService(prefs.getBaseUrl(), prefs.getToken())

    /**
     * Full offline-first sync:
     *   PUSH: unsynced habits → /api/mobile/habits/sync
     *   PUSH: unsynced habit ticks → /api/mobile/habits/tick
     *   PUSH: unsynced goals → /api/mobile/goals/sync
     *   PUSH: unsynced reminders → /api/mobile/reminders/sync
     *   PUSH: unsynced reel config → /api/mobile/reel-controller/sync
     *   PULL: full server state (habits + logs + goals + reminders) → Room
     *   PULL: user profile (name, email, plan, streak) → UserProfileEntity in Room
     */
    suspend fun syncWithServer(): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncing.compareAndSet(false, true)) {
            Log.d(TAG, "syncWithServer: already in-flight, skipping")
            return@withContext false
        }
        val token = prefs.getToken() ?: run {
            isSyncing.set(false)
            Log.w(TAG, "No token — skipping sync")
            return@withContext false
        }
        Log.d(TAG, "syncWithServer: authenticated sync started")
        try {

            // ── 1. Push offline habit ticks ──────────────────────────────────────
            val unsyncedLogs = db.habitLogDao().getUnsyncedLogs()
            val initiallyUnsyncedLogIds = unsyncedLogs.map { it.id }.toSet()
            if (unsyncedLogs.isNotEmpty()) {
                try {
                    val payload = JsonObject()
                    val logsArr = JsonArray()
                    unsyncedLogs.forEach { log ->
                        logsArr.add(JsonObject().apply {
                            addProperty("habitId", log.habitId)
                            addProperty("date",    log.date)
                            addProperty("done",    log.done)
                        })
                    }
                    payload.add("logs", logsArr)
                    val res = api().syncHabitLogs(payload)
                    if (res["success"]?.asBoolean == true) {
                        db.habitLogDao().markLogsSynced(unsyncedLogs.map { it.id })
                        Log.d(TAG, "Pushed ${unsyncedLogs.size} offline ticks")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Habit tick push failed (non-fatal): ${e.message}")
                }
            }

            // ── 1.5 Push unsynced habits (creates, edits, soft-deletes) ──────────
            val unsyncedHabits = db.habitDao().getUnsyncedHabits()
            if (unsyncedHabits.isNotEmpty()) {
                val upsertsArr = JsonArray()
                val deletesArr = JsonArray()
                unsyncedHabits.forEach { h ->
                    if (h.isDeleted) {
                        h.serverId?.let { deletesArr.add(it) }
                    } else {
                        upsertsArr.add(JsonObject().apply {
                            addProperty("localId",       h.id)
                            if (h.serverId != null) addProperty("serverId", h.serverId)
                            addProperty("title",         h.title)
                            addProperty("scheduledTime", h.scheduledTime ?: "")
                            addProperty("isActive",      h.isActive)
                        })
                    }
                }
                val habitPayload = JsonObject().apply {
                    add("upserts", upsertsArr)
                    add("deletes", deletesArr)
                }
                try {
                    val res = api().syncHabits(habitPayload)
                    if (res["success"]?.asBoolean == true) {
                        val upsertedArr = res["upserted"]?.asJsonArray
                        upsertedArr?.forEach { elem ->
                            val obj      = elem.asJsonObject
                            val localId  = obj["localId"].asString
                            val serverId = obj["serverId"]?.takeIf { !it.isJsonNull }?.asString
                            val status   = obj["status"].asString
                            if (status == "created" && localId != serverId) {
                                // Server assigned a real cuid — seamlessly replace the local placeholder row
                                val localHabit = db.habitDao().getHabitById(localId)
                                if (localHabit != null && serverId != null) {
                                    db.habitDao().insertHabits(listOf(localHabit.copy(
                                        id = serverId,
                                        serverId = serverId,
                                        isSynced = true
                                    )))
                                }
                                db.habitDao().deleteHabitById(localId)
                            } else {
                                db.habitDao().markHabitsSynced(listOf(localId))
                            }
                        }
                        // Remove local-only deleted habits (never had a serverId)
                        unsyncedHabits.filter { it.isDeleted && it.serverId == null }.forEach {
                            db.habitDao().markHabitsSynced(listOf(it.id))
                        }
                        Log.d(TAG, "Pushed ${upsertsArr.size()} habits, ${deletesArr.size()} deletes")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Habit push failed (non-fatal): ${e.message}")
                }
            }

            // ── 1.75 Push offline goals ───────────────────────────────────────────
            val unsyncedGoals = db.goalDao().getUnsyncedGoals()
            if (unsyncedGoals.isNotEmpty()) {
                val payload    = JsonObject()
                val upsertsArr = JsonArray()
                val deletesArr = JsonArray()
                unsyncedGoals.forEach { g ->
                    if (g.isDeleted) {
                        g.serverId?.let { deletesArr.add(it) }
                    } else {
                        upsertsArr.add(JsonObject().apply {
                            addProperty("localId",     g.id)
                            if (g.serverId != null) addProperty("serverId", g.serverId)
                            addProperty("title",       g.title)
                            addProperty("category",    g.category)
                            addProperty("progress",    g.progress)
                            addProperty("isCompleted", g.isCompleted)
                            addProperty("isPinned",    g.isPinned)
                            try { add("subGoals", JsonParser.parseString(g.subGoalsJson)) } catch (e: Exception) {}
                        })
                    }
                }
                payload.add("upserts", upsertsArr)
                payload.add("deletes", deletesArr)
                try {
                    val res = api().syncGoals(payload)
                    if (res["success"]?.asBoolean == true) {
                        val upsertedArr = res["upserted"]?.asJsonArray
                        upsertedArr?.forEach { elem ->
                            val obj      = elem.asJsonObject
                            val localId  = obj["localId"].asString
                            val serverId = obj["serverId"]?.takeIf { !it.isJsonNull }?.asString
                            val status   = obj["status"].asString
                            if (status == "created" && localId != serverId && serverId != null) {
                                // Server assigned a real cuid — replace the local placeholder row
                                // IMPORTANT: Insert the server-ID row BEFORE deleting the local one
                                val localGoal = db.goalDao().getGoalById(localId)
                                if (localGoal != null) {
                                    db.goalDao().insertGoals(listOf(localGoal.copy(
                                        id       = serverId,
                                        serverId = serverId,
                                        isSynced = true
                                    )))
                                }
                                db.goalDao().deleteGoalById(localId)
                            } else {
                                db.goalDao().markGoalsSynced(listOf(localId))
                            }
                        }
                        unsyncedGoals.filter { it.isDeleted }.forEach { db.goalDao().deleteGoalById(it.id) }
                        Log.d(TAG, "Pushed ${upsertsArr.size()} goals, ${deletesArr.size()} deletes")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Goal sync push failed: ${e.message}")
                }
            }

            // ── 1.85 Push offline reminders ───────────────────────────────────────
            val unsyncedReminders = db.reminderDao().getUnsyncedReminders()
            if (unsyncedReminders.isNotEmpty()) {
                val payload    = JsonObject()
                val upsertsArr = JsonArray()
                val deletesArr = JsonArray()
                unsyncedReminders.forEach { r ->
                    if (r.isDeleted) {
                        r.serverId?.let { deletesArr.add(it) }
                    } else {
                        upsertsArr.add(JsonObject().apply {
                            addProperty("localId",    r.id)
                            if (r.serverId != null) addProperty("serverId", r.serverId)
                            addProperty("title",      r.title)
                            addProperty("description",r.description)
                            addProperty("startDate",  r.startDate)
                            addProperty("endDate",    r.endDate)
                            addProperty("startTime",  r.startTime)
                            addProperty("endTime",    r.endTime)
                            addProperty("isFullDay",  r.isFullDay)
                            addProperty("priority",   r.priority)
                            addProperty("markerColor",r.markerColor)
                        })
                    }
                }
                payload.add("upserts", upsertsArr)
                payload.add("deletes", deletesArr)
                try {
                    val res = api().syncReminders(payload)
                    if (res["success"]?.asBoolean == true) {
                        val upsertedArr = res["upserted"]?.asJsonArray
                        val syncedIds   = mutableListOf<String>()
                        upsertedArr?.forEach { elem ->
                            val obj      = elem.asJsonObject
                            val localId  = obj["localId"].asString
                            val serverId = obj["serverId"]?.takeIf { !it.isJsonNull }?.asString
                            val status   = obj["status"].asString
                            if (status == "created" && localId != serverId) {
                                db.reminderDao().deleteReminderById(localId)
                            } else {
                                syncedIds.add(localId)
                            }
                        }
                        if (syncedIds.isNotEmpty()) db.reminderDao().markRemindersSynced(syncedIds)
                        unsyncedReminders.filter { it.isDeleted }.forEach { db.reminderDao().deleteReminderById(it.id) }
                        Log.d(TAG, "Pushed ${upsertsArr.size()} reminders, ${deletesArr.size()} deletes")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reminder sync push failed: ${e.message}")
                }
            }

            // ── 1.9 Push unsynced reel controller config ──────────────────────────
            val unsyncedReelApps = db.reelControllerDao().getUnsyncedApps()
            val globalReelConfig = db.reelControllerDao().getGlobalConfig()
            val hasUnsyncedReel  = unsyncedReelApps.isNotEmpty() || (globalReelConfig?.isSynced == false)
            if (hasUnsyncedReel) {
                val payload = JsonObject()
                globalReelConfig?.let { cfg ->
                    payload.addProperty("isFeatureEnabled",   cfg.isFeatureEnabled)
                    payload.addProperty("isBlockModeEnabled", cfg.isBlockModeEnabled)
                    payload.addProperty("isHardBlockEnabled", cfg.isHardBlockEnabled)
                }
                val appsArr = JsonArray()
                unsyncedReelApps.forEach { app ->
                    appsArr.add(JsonObject().apply {
                        addProperty("pkg",       app.pkg)
                        addProperty("reelLimit", app.reelLimit)
                        addProperty("timeLimit", app.timeLimit)
                        addProperty("isEnabled", app.isEnabled)
                    })
                }
                payload.add("apps", appsArr)
                try {
                    val res = api().syncReelController(payload)
                    if (res["success"]?.asBoolean == true) {
                        if (unsyncedReelApps.isNotEmpty())
                            db.reelControllerDao().markAppsSynced(unsyncedReelApps.map { it.pkg })
                        if (globalReelConfig?.isSynced == false)
                            db.reelControllerDao().markGlobalSynced()
                        Log.d(TAG, "Pushed reel controller: ${unsyncedReelApps.size} apps + global")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reel controller sync push failed (non-fatal): ${e.message}")
                }
            }

            // ── 2. Fetch full server state ────────────────────────────────────────
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val baseUrl      = prefs.getBaseUrl().trimEnd('/')
            val tz           = java.util.TimeZone.getDefault().id
            val deviceDate   = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val url          = "$baseUrl/api/wallpaper-data" +
                               "?tz=${java.net.URLEncoder.encode(tz, "UTF-8")}" +
                               "&deviceDate=$deviceDate"
            Log.d(TAG, "Fetching wallpaper-data for date=$deviceDate, tz=$tz")

            val request  = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Cookie", "publicToken=$token; native_auth=true")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body     = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Wallpaper data request failed with HTTP ${response.code}")
                return@withContext false
            }

            val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

            // ── 3. Parse & persist user profile into Room (fully offline access) ──
            val statsObj = root["stats"]?.takeIf { it.isJsonObject }?.asJsonObject
            val userObj  = root["user"]?.takeIf { it.isJsonObject }?.asJsonObject

            val streak        = statsObj?.get("streak")?.asInt                       ?: 0
            val totalHabits   = statsObj?.get("totalHabits")?.asInt                  ?: 0
            val todayPct      = statsObj?.get("todayCompletionPercentage")?.asInt    ?: 0
            val userName      = userObj?.get("name")
                                        ?.takeIf { !it.isJsonNull }?.asString        ?: ""
            val userEmail     = userObj?.get("email")
                                        ?.takeIf { !it.isJsonNull }?.asString        ?: ""
            
            // Full wallpaper settings object
            val settingsObj   = root["settings"]?.takeIf { !it.isJsonNull }?.takeIf { it.isJsonObject }?.asJsonObject

            // Plan lives in user object or settings object on the backend
            val userPlan      = settingsObj?.get("plan")?.takeIf { !it.isJsonNull }?.asString
                                ?: userObj?.get("plan")?.takeIf { !it.isJsonNull }?.asString
                                ?: "free"

            // Save wallpaper settings so the App/Widgets use the correct theme (e.g. green)
            if (settingsObj != null) {
                prefs.saveWallpaperSettings(settingsObj.toString())
            }

            // Keep legacy SharedPrefs in sync for existing consumers
            prefs.setCurrentStreak(streak)
            prefs.setDaysTracked(totalHabits)

            // Persist into Room — survives app restarts without a network call
            val existingProfile = db.userProfileDao().get()
            val finalName  = userName.ifBlank { existingProfile?.name ?: "" }
            val finalEmail = userEmail.ifBlank { existingProfile?.email ?: "" }
            db.userProfileDao().upsert(
                UserProfileEntity(
                    id              = 1,
                    name            = finalName,
                    email           = finalEmail,
                    plan            = userPlan,
                    isPremium       = userPlan != "free",
                    currentStreak   = if (streak > 0) streak else (existingProfile?.currentStreak ?: 0),
                    totalHabits     = if (totalHabits > 0) totalHabits else (existingProfile?.totalHabits ?: 0),
                    todayCompletion = todayPct,
                    daysTracked     = if (totalHabits > 0) totalHabits else (existingProfile?.daysTracked ?: 0),
                    publicToken     = token,
                    updatedAt       = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "User profile cached successfully")

            // ── 4. Parse Habits ───────────────────────────────────────────────────
            root["data"]?.takeIf { it.isJsonObject }?.asJsonObject?.get("habits")?.takeIf { it.isJsonArray }?.asJsonArray?.let { arr ->
                val entities    = mutableListOf<HabitEntity>()
                val logEntities = mutableListOf<HabitLogEntity>()
                for (elem in arr) {
                    if (!elem.isJsonObject) continue
                    val h       = elem.asJsonObject
                    val habitId = h["id"]?.takeIf { !it.isJsonNull }?.asString ?: continue
                    entities.add(HabitEntity(
                        id            = habitId,
                        serverId      = habitId,
                        title         = h["title"]?.takeIf { !it.isJsonNull }?.asString ?: "Untitled",
                        scheduledTime = h["scheduledTime"]?.takeIf { !it.isJsonNull }?.asString,
                        isActive      = true,
                        isSynced      = true
                    ))
                    val logsArr = h["logs"]?.takeIf { it.isJsonArray }?.asJsonArray
                    if (logsArr != null) {
                        for (logElem in logsArr) {
                            if (!logElem.isJsonObject) continue
                            val log     = logElem.asJsonObject
                            val dateRaw = log["date"]?.takeIf { !it.isJsonNull }?.asString ?: continue
                            val dateStr = dateRaw.take(10)
                            logEntities.add(HabitLogEntity(
                                id       = "${habitId}_${dateStr}",
                                habitId  = habitId,
                                date     = dateStr,
                                done     = log["done"]?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                                isSynced = true
                            ))
                        }
                    }
                }
                // Protect locally unsynced logs (and those we JUST pushed) from being overwritten by potentially stale server pull
                val currentUnsyncedLogIds = db.habitLogDao().getUnsyncedLogs().map { it.id }.toSet()
                val protectedLogIds = initiallyUnsyncedLogIds + currentUnsyncedLogIds
                
                // CRITICAL FIX: NEVER let the server overwrite a local `done=true` with a `done=false`.
                // If the backend fails to save our tick, it will return `done=false` on the next pull.
                // We trust the local DB as the source of truth for done ticks.
                val localDoneLogIds = db.habitLogDao().getAllDoneLogs().map { it.id }.toSet()
                
                if (protectedLogIds.isNotEmpty())
                    Log.d(TAG, "Protecting ${protectedLogIds.size} local tick(s) from server overwrite")

                db.habitDao().insertHabits(entities)
                val logsToInsert = logEntities.filter { it.id !in protectedLogIds && it.id !in localDoneLogIds }
                if (logsToInsert.isNotEmpty()) db.habitLogDao().insertLogs(logsToInsert)
                Log.d(TAG, "Saved ${entities.size} habits, ${logsToInsert.size} logs (${logEntities.size - logsToInsert.size} protected)")
            }

            // ── 5. Parse Goals (full list via /api/goals) ─────────────────────────
            try {
                val goalsUrl = "$baseUrl/api/goals"
                val goalsReq = Request.Builder()
                    .url(goalsUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Cookie", "publicToken=$token; native_auth=true")
                    .get()
                    .build()
                val goalsRes = httpClient.newCall(goalsReq).execute()
                val goalsBody = goalsRes.body?.string() ?: "[]"
                if (goalsRes.isSuccessful) {
                    val unsyncedLocalIds = db.goalDao().getUnsyncedGoalIds().toSet()
                    if (unsyncedLocalIds.isNotEmpty())
                        Log.d(TAG, "Protecting ${unsyncedLocalIds.size} locally unsynced goal(s) from server overwrite")

                    val goalsArr     = JsonParser.parseString(goalsBody).asJsonArray
                    val goalsEntities = goalsArr.mapNotNull { elem ->
                        runCatching {
                            val g   = elem.asJsonObject
                            val sId = g["id"].asString
                            if (sId in unsyncedLocalIds) return@runCatching null

                            val subGoalsStr = g["subGoals"]?.asJsonArray?.toString() ?: "[]"
                            GoalEntity(
                                id           = sId,
                                serverId     = sId,
                                title        = g["title"].asString,
                                progress     = g["progress"]?.asInt ?: 0,
                                category     = g["category"]?.takeIf { !it.isJsonNull }?.asString ?: "General",
                                isCompleted  = g["isCompleted"]?.asBoolean ?: false,
                                isPinned     = g["isPinned"]?.asBoolean ?: false,
                                subGoalsJson = subGoalsStr,
                                isSynced     = true,
                                isDeleted    = false
                            )
                        }.getOrNull()
                    }
                    if (goalsEntities.isNotEmpty()) {
                        db.goalDao().insertGoals(goalsEntities)
                        Log.d(TAG, "Pulled ${goalsEntities.size} goal(s) from server")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed fetching goals list: ${e.message}")
            }

            // ── 6. Parse Reminders ────────────────────────────────────────────────
            root["data"]?.asJsonObject?.get("reminders")?.asJsonArray?.let { arr ->
                val reminders = arr.mapNotNull { elem ->
                    runCatching {
                        val r   = elem.asJsonObject
                        val sId = r["id"].asString
                        ReminderEntity(
                            id          = sId,
                            serverId    = sId,
                            title       = r["title"].asString,
                            description = r["description"]?.takeIf { !it.isJsonNull }?.asString,
                            startDate   = r["startDate"].asString.take(10),
                            endDate     = r["endDate"]?.takeIf { !it.isJsonNull }?.asString?.take(10)
                                          ?: r["startDate"].asString.take(10),
                            startTime   = r["startTime"]?.takeIf { !it.isJsonNull }?.asString,
                            endTime     = r["endTime"]?.takeIf { !it.isJsonNull }?.asString,
                            isFullDay   = r["isFullDay"]?.asBoolean ?: true,
                            priority    = r["priority"]?.asInt ?: 0,
                            markerColor = r["markerColor"]?.takeIf { !it.isJsonNull }?.asString ?: "#FF7A00",
                            isSynced    = true,
                            isDeleted   = false
                        )
                    }.getOrNull()
                }
                if (reminders.isNotEmpty()) db.reminderDao().insertReminders(reminders)
                Log.d(TAG, "Saved ${reminders.size} reminder(s)")
            }

            // ── 7. Fetch Reel Controller config from server ───────────────────────
            try {
                val reelRes = api().getReelController()
                if (reelRes["success"]?.asBoolean == true) {
                    val data = reelRes["data"]?.asJsonObject
                    val appEntities = data?.get("apps")?.asJsonArray?.mapNotNull { elem ->
                        runCatching {
                            val a = elem.asJsonObject
                            ReelControllerEntity(
                                pkg       = a["pkg"].asString,
                                reelLimit = a["reelLimit"]?.asInt ?: 30,
                                timeLimit = a["timeLimit"]?.asInt ?: 20,
                                isEnabled = a["isEnabled"]?.asBoolean ?: true,
                                isSynced  = true
                            )
                        }.getOrNull()
                    } ?: emptyList()
                    reelControllerRepo.applyServerConfig(
                        isFeatureEnabled   = data?.get("isFeatureEnabled")?.asBoolean   ?: true,
                        isBlockModeEnabled = data?.get("isBlockModeEnabled")?.asBoolean ?: false,
                        isHardBlockEnabled = data?.get("isHardBlockEnabled")?.asBoolean ?: false,
                        appSettings        = appEntities
                    )
                    Log.d(TAG, "✅ Reel controller fetched: ${appEntities.size} apps")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reel controller fetch failed (non-fatal): ${e.message}")
            }

            Log.d(TAG, "✅ Full sync complete")

            // Broadcast so widgets & wallpaper refresh instantly
            com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            false
        } finally {
            isSyncing.set(false)
        }
    }
}
