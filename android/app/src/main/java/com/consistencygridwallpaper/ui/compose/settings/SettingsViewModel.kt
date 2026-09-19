package com.consistencygridwallpaper.ui.compose.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.UserProfileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)

    /**
     * Live stream of the cached user profile from Room.
     * Returns a default empty profile until the first sync completes.
     * Works 100% offline — no network call needed.
     */
    val profile: StateFlow<UserProfileEntity> = db.userProfileDao().getFlow()
        .map { it ?: UserProfileEntity() }
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5000),
            initialValue   = UserProfileEntity()
        )

    fun updateName(newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = db.userProfileDao().get() ?: UserProfileEntity()
            db.userProfileDao().upsert(current.copy(name = newName))
        }
    }
}
